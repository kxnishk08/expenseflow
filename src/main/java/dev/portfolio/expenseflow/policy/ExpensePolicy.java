package dev.portfolio.expenseflow.policy;

import dev.portfolio.expenseflow.common.*;
import dev.portfolio.expenseflow.report.ExpenseItem;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExpensePolicy {
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

  public record Limit(BigDecimal cap, boolean receiptRequired) {}

  public record Version(UUID id, String version, Map<Category, Limit> limits) {}

  private final JdbcTemplate jdbc;

  public ExpensePolicy(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Version current() {
    var row = jdbc.queryForMap("select id,version from expense_policies where active");
    UUID id = (UUID) row.get("id");
    Map<Category, Limit> limits = new EnumMap<>(Category.class);
    jdbc.query(
        "select category,per_item_limit,receipt_required from policy_category_limits where"
            + " policy_id=?",
        rs -> {
          limits.put(
              Category.valueOf(rs.getString(1)), new Limit(rs.getBigDecimal(2), rs.getBoolean(3)));
        },
        id);
    return new Version(id, (String) row.get("version"), Map.copyOf(limits));
  }

  public static BigDecimal validate(List<ExpenseItem> items, Version policy, Instant now) {
    LocalDate today = now.atZone(BUSINESS_ZONE).toLocalDate();
    List<ApiError.FieldError> errors = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    if (items.isEmpty() || items.size() > 50)
      errors.add(new ApiError.FieldError("items", "A submission needs 1-50 items."));
    for (ExpenseItem item : items) {
      String field = "items." + item.id;
      Limit limit = policy.limits().get(item.category);
      if (item.amount.signum() <= 0
          || item.amount.scale() > 2
          || item.amount.compareTo(limit.cap()) > 0)
        errors.add(
            new ApiError.FieldError(field + ".amount", "Amount exceeds policy or precision."));
      if (item.expenseDate.isAfter(today) || item.expenseDate.isBefore(today.minusDays(90)))
        errors.add(
            new ApiError.FieldError(
                field + ".expenseDate",
                "Expense must be within the last 90 calendar days, inclusive."));
      if (limit.receiptRequired()
          && (item.receiptReference == null || item.receiptReference.isBlank()))
        errors.add(
            new ApiError.FieldError(field + ".receiptReference", "Receipt reference required."));
      total = total.add(item.amount);
    }
    if (total.signum() <= 0 || total.compareTo(new BigDecimal("25000")) > 0)
      errors.add(
          new ApiError.FieldError("total", "Total must be positive and at most INR 25,000.00."));
    if (!errors.isEmpty())
      throw new DomainException(
          409, "POLICY_VIOLATION", "Expense policy validation failed.", errors);
    return total.setScale(2);
  }
}
