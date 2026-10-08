package dev.portfolio.expenseflow.report;

import dev.portfolio.expenseflow.common.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportViews {
  public record Item(
      UUID id,
      String category,
      LocalDate expenseDate,
      String merchant,
      String description,
      BigDecimal amount,
      String receiptReference,
      BigDecimal appliedCap,
      Boolean receiptRequired) {}

  public record Submission(
      UUID id,
      int submissionNumber,
      UUID approverId,
      UUID policyId,
      String title,
      BigDecimal total,
      Instant submittedAt,
      List<Item> items) {}

  public record Event(
      UUID id,
      UUID submissionId,
      UUID actorId,
      String eventType,
      String fromState,
      String toState,
      String comment,
      Instant createdAt) {}

  public record Reimbursement(
      UUID id,
      String reference,
      BigDecimal amount,
      String currency,
      Instant recordedAt,
      UUID recordedBy) {}

  public record Owner(
      UUID id,
      String title,
      ReportState state,
      long revision,
      int currentSubmissionNumber,
      List<Item> items,
      List<Submission> submissions,
      List<Event> events,
      Reimbursement reimbursement) {}

  public record Reviewer(
      UUID reportId,
      ReportState state,
      long revision,
      List<Submission> submissions,
      List<Event> events,
      Reimbursement reimbursement) {}

  public record Brief(
      UUID id,
      String title,
      String state,
      long revision,
      int currentSubmissionNumber,
      Instant createdAt,
      BigDecimal total) {}

  private final JdbcTemplate jdbc;

  public ReportViews(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Item> items(UUID report) {
    return jdbc.query(
        "select * from expense_items where report_id=? order by id",
        (rs, n) ->
            new Item(
                rs.getObject("id", UUID.class),
                rs.getString("category"),
                rs.getObject("expense_date", LocalDate.class),
                rs.getString("merchant"),
                rs.getString("description"),
                rs.getBigDecimal("amount"),
                rs.getString("receipt_reference"),
                null,
                null),
        report);
  }

  public List<Submission> submissions(UUID report, UUID manager, UUID financeReturnedBy) {
    String filter =
        manager != null
            ? " and s.approver_id=?"
            : financeReturnedBy != null
                ? " and exists(select 1 from report_events e where e.submission_id=s.id and"
                    + " e.event_type='FINANCE_RETURN' and e.actor_id=?)"
                : "";
    List<Object> params = new ArrayList<>();
    params.add(report);
    if (manager != null) params.add(manager);
    if (financeReturnedBy != null) params.add(financeReturnedBy);
    // Two bulk queries, regardless of submission/item count; no graph traversal.
    var snapshots =
        jdbc.query(
            "select i.* from submitted_expense_items i join report_submissions s on"
                + " s.id=i.submission_id where s.report_id=?"
                + filter
                + " order by s.submission_number,i.id",
            (rs, n) ->
                Map.entry(
                    rs.getObject("submission_id", UUID.class),
                    new Item(
                        rs.getObject("id", UUID.class),
                        rs.getString("category"),
                        rs.getObject("expense_date", LocalDate.class),
                        rs.getString("merchant"),
                        rs.getString("description"),
                        rs.getBigDecimal("amount"),
                        rs.getString("receipt_reference"),
                        rs.getBigDecimal("applied_cap"),
                        rs.getBoolean("receipt_required"))),
            params.toArray());
    Map<UUID, List<Item>> bySubmission = new HashMap<>();
    snapshots.forEach(
        e -> bySubmission.computeIfAbsent(e.getKey(), x -> new ArrayList<>()).add(e.getValue()));
    return jdbc.query(
        "select s.* from report_submissions s where s.report_id=?"
            + filter
            + " order by s.submission_number",
        (rs, n) ->
            new Submission(
                rs.getObject("id", UUID.class),
                rs.getInt("submission_number"),
                rs.getObject("approver_id", UUID.class),
                rs.getObject("policy_id", UUID.class),
                rs.getString("title_snapshot"),
                rs.getBigDecimal("total"),
                rs.getTimestamp("submitted_at").toInstant(),
                bySubmission.getOrDefault(rs.getObject("id", UUID.class), List.of())),
        params.toArray());
  }

  public List<Event> events(UUID report, UUID manager, UUID returnedBy) {
    String filter =
        manager != null
            ? " and exists(select 1 from report_submissions s where s.id=e.submission_id and"
                + " s.approver_id=?)"
            : returnedBy != null
                ? " and exists(select 1 from report_events f where f.submission_id=e.submission_id"
                    + " and f.event_type='FINANCE_RETURN' and f.actor_id=?)"
                : "";
    List<Object> params = new ArrayList<>();
    params.add(report);
    if (manager != null) params.add(manager);
    if (returnedBy != null) params.add(returnedBy);
    return jdbc.query(
        "select e.* from report_events e where e.report_id=?"
            + filter
            + " order by e.created_at,e.id",
        (rs, n) ->
            new Event(
                rs.getObject("id", UUID.class),
                rs.getObject("submission_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("from_state"),
                rs.getString("to_state"),
                rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant()),
        params.toArray());
  }

  public Reimbursement reimbursement(UUID report) {
    return jdbc
        .query(
            "select * from reimbursements where report_id=?",
            (rs, n) ->
                new Reimbursement(
                    rs.getObject("id", UUID.class),
                    rs.getString("reference"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency"),
                    rs.getTimestamp("recorded_at").toInstant(),
                    rs.getObject("recorded_by", UUID.class)),
            report)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public Owner owner(ExpenseReport r) {
    return new Owner(
        r.id,
        r.title,
        r.state,
        r.revision,
        r.currentSubmissionNumber,
        items(r.id),
        submissions(r.id, null, null),
        events(r.id, null, null),
        reimbursement(r.id));
  }

  public Reviewer reviewer(ExpenseReport r, UUID manager, UUID returnedBy) {
    var submissions = submissions(r.id, manager, returnedBy);
    if (submissions.isEmpty()) throw DomainException.missing();
    // Audit views cannot expose a reimbursement belonging to a later, unauthorized submission.
    Reimbursement reimbursement =
        manager == null && returnedBy == null ? reimbursement(r.id) : null;
    return new Reviewer(
        r.id, r.state, r.revision, submissions, events(r.id, manager, returnedBy), reimbursement);
  }

  public PageResult<Brief> list(String where, List<Object> params, int page, int size) {
    PageResult.validate(page, size);
    Long count =
        jdbc.queryForObject(
            "select count(*) from expense_reports r " + where, Long.class, params.toArray());
    List<Object> paged = new ArrayList<>(params);
    paged.add(size);
    paged.add((long) page * size);
    var rows =
        jdbc.query(
            "select r.*,s.total from expense_reports r left join report_submissions s on"
                + " s.report_id=r.id and s.submission_number=r.current_submission_number "
                + where
                + " order by r.created_at desc,r.id limit ? offset ?",
            (rs, n) ->
                new Brief(
                    rs.getObject("id", UUID.class),
                    rs.getString("title"),
                    rs.getString("state"),
                    rs.getLong("revision"),
                    rs.getInt("current_submission_number"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getBigDecimal("total")),
            paged.toArray());
    return new PageResult<>(rows, page, size, count);
  }
}
