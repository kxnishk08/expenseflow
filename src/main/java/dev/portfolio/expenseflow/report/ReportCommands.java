package dev.portfolio.expenseflow.report;

import dev.portfolio.expenseflow.policy.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReportCommands {
  private ReportCommands() {}

  public record Create(@NotBlank @Size(max = 120) String title) {}

  public record Title(
      @NotNull @Min(0) Long expectedRevision, @NotBlank @Size(max = 120) String title) {}

  public record Revision(@NotNull @Min(0) Long expectedRevision) {}

  public record Withdraw(
      @NotNull @Min(0) Long expectedRevision, @Size(max = 1000) String comment) {}

  public record Decision(
      @NotNull @Min(0) Long expectedRevision,
      @NotBlank String decision,
      @Size(max = 1000) String comment) {}

  public record Reimbursement(
      @NotNull @Min(0) Long expectedRevision, @NotBlank @Size(max = 64) String reference) {}

  public record Item(
      @NotNull @Min(0) Long expectedRevision,
      @NotNull Category category,
      @NotNull LocalDate expenseDate,
      @NotBlank @Size(max = 120) String merchant,
      @NotBlank @Size(max = 500) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 100) String receiptReference) {}
}
