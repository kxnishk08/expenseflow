package dev.portfolio.expenseflow.report;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expense_reports")
public class ExpenseReport {
  @Id public UUID id;

  @Column(name = "owner_id")
  public UUID ownerId;

  public String title;

  @Enumerated(EnumType.STRING)
  public ReportState state;

  public long revision;

  @Column(name = "current_submission_number")
  public int currentSubmissionNumber;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "updated_at")
  public Instant updatedAt;

  protected ExpenseReport() {}

  public ExpenseReport(UUID owner, String title, Instant now) {
    id = UUID.randomUUID();
    ownerId = owner;
    this.title = title;
    state = ReportState.DRAFT;
    createdAt = now;
    updatedAt = now;
  }

  public void touch(Instant now) {
    revision++;
    updatedAt = now;
  }
}
