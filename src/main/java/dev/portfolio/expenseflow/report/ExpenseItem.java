package dev.portfolio.expenseflow.report;

import dev.portfolio.expenseflow.policy.Category;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expense_items")
public class ExpenseItem {
  @Id public UUID id;

  @Column(name = "report_id")
  public UUID reportId;

  @Enumerated(EnumType.STRING)
  public Category category;

  @Column(name = "expense_date")
  public LocalDate expenseDate;

  public String merchant;
  public String description;

  @Column(precision = 12, scale = 2)
  public BigDecimal amount;

  @Column(name = "receipt_reference")
  public String receiptReference;

  protected ExpenseItem() {}

  public ExpenseItem(UUID reportId) {
    id = UUID.randomUUID();
    this.reportId = reportId;
  }

  public void replace(ReportCommands.Item body) {
    category = body.category();
    expenseDate = body.expenseDate();
    merchant = body.merchant().trim();
    description = body.description().trim();
    amount = body.amount();
    receiptReference = body.receiptReference().trim();
  }
}
