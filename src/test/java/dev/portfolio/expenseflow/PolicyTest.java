package dev.portfolio.expenseflow;

import static org.assertj.core.api.Assertions.*;

import dev.portfolio.expenseflow.common.DomainException;
import dev.portfolio.expenseflow.policy.*;
import dev.portfolio.expenseflow.reimbursement.ReimbursementService;
import dev.portfolio.expenseflow.report.*;
import dev.portfolio.expenseflow.reporting.SummaryService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PolicyTest {
  static final Instant NOW = Instant.parse("2026-10-08T18:30:00Z");
  static final ExpensePolicy.Version POLICY =
      new ExpensePolicy.Version(
          UUID.randomUUID(),
          "POLICY-2026-01",
          Map.of(
              Category.TRAVEL,
              new ExpensePolicy.Limit(new BigDecimal("2000"), true),
              Category.MEALS,
              new ExpensePolicy.Limit(new BigDecimal("750"), true),
              Category.OFFICE_SUPPLIES,
              new ExpensePolicy.Limit(new BigDecimal("5000"), true)));

  static ExpenseItem item(Category category, String amount, LocalDate date) {
    var i = new ExpenseItem(UUID.randomUUID());
    i.category = category;
    i.amount = new BigDecimal(amount);
    i.expenseDate = date;
    i.receiptReference = "receipt";
    return i;
  }

  @Test
  void capEqualityAndNinetyDayCutoffAreInclusive() {
    assertThat(
            ExpensePolicy.validate(
                List.of(item(Category.TRAVEL, "2000.00", LocalDate.of(2026, 7, 11))), POLICY, NOW))
        .isEqualByComparingTo("2000.00");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2000.01", "1.001", "0.00"})
  void rejectsAmount(String value) {
    assertThatThrownBy(
            () ->
                ExpensePolicy.validate(
                    List.of(item(Category.TRAVEL, value, LocalDate.of(2026, 10, 9))), POLICY, NOW))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void midnightAdvancesBusinessDateInKolkata() {
    var i = item(Category.TRAVEL, "600", LocalDate.of(2026, 10, 9));
    assertThatThrownBy(() -> ExpensePolicy.validate(List.of(i), POLICY, NOW.minusSeconds(1)))
        .isInstanceOf(DomainException.class);
    assertThat(ExpensePolicy.validate(List.of(i), POLICY, NOW)).isEqualByComparingTo("600");
  }

  @Test
  void exactTotalAndBounds() {
    assertThat(
            ExpensePolicy.validate(
                List.of(
                    item(Category.TRAVEL, "600.00", LocalDate.of(2026, 10, 9)),
                    item(Category.MEALS, "450.00", LocalDate.of(2026, 10, 9))),
                POLICY,
                NOW))
        .isEqualByComparingTo("1050.00");
    assertThat(
            ExpensePolicy.validate(
                java.util.stream.IntStream.range(0, 5)
                    .mapToObj(
                        n -> item(Category.OFFICE_SUPPLIES, "5000", LocalDate.of(2026, 10, 9)))
                    .toList(),
                POLICY,
                NOW))
        .isEqualByComparingTo("25000");
    assertThatThrownBy(
            () ->
                ExpensePolicy.validate(
                    java.util.stream.IntStream.range(0, 6)
                        .mapToObj(
                            n -> item(Category.OFFICE_SUPPLIES, "5000", LocalDate.of(2026, 10, 9)))
                        .toList(),
                    POLICY,
                    NOW))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void referenceNormalizationAndRange() {
    assertThat(ReimbursementService.normalize(" demo-ref-0001 ")).isEqualTo("DEMO-REF-0001");
    assertThatThrownBy(() -> ReimbursementService.normalize("ref!bad"))
        .isInstanceOf(IllegalArgumentException.class);
    var b = SummaryService.bounds(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10));
    assertThat(b[0].toInstant()).isEqualTo(NOW);
    assertThatThrownBy(
            () -> SummaryService.bounds(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void revisionAndStateChecks() {
    var r = new ExpenseReport(UUID.randomUUID(), "title", NOW);
    r.revision = 4;
    assertThatThrownBy(() -> ReportService.revision(r, 3))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("STALE_REPORT_REVISION");
    assertThatThrownBy(() -> ReportService.state(r, ReportState.SUBMITTED))
        .isInstanceOf(DomainException.class);
  }
}
