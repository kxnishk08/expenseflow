package dev.portfolio.expenseflow.reimbursement;

import dev.portfolio.expenseflow.common.*;
import dev.portfolio.expenseflow.identity.Actor;
import dev.portfolio.expenseflow.report.*;
import dev.portfolio.expenseflow.review.ReviewService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReimbursementService {
  private final ReportService workflow;
  private final ReportViews views;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final OperationProbe probe;

  public ReimbursementService(
      ReportService workflow,
      ReportViews views,
      JdbcTemplate jdbc,
      Clock clock,
      OperationProbe probe) {
    this.workflow = workflow;
    this.views = views;
    this.jdbc = jdbc;
    this.clock = clock;
    this.probe = probe;
  }

  public static String normalize(String reference) {
    String normalized = reference.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z0-9-]{8,64}"))
      throw new IllegalArgumentException("Invalid reimbursement reference");
    return normalized;
  }

  public record Result(ReportViews.Reimbursement reimbursement, long reportRevision) {}

  @Transactional
  public Result record(Actor actor, UUID id, ReportCommands.Reimbursement body) {
    var r = workflow.locked(id);
    ReviewService.finance(actor, r);
    ReportService.revision(r, body.expectedRevision());
    ReportService.state(r, ReportState.FINANCE_ACCEPTED);
    UUID submission = workflow.submission(r);
    jdbc.update(
        "insert into"
            + " reimbursements(id,report_id,submission_id,recorded_by,reference,amount,currency,recorded_at)"
            + " select ?,?,?,?,?,total,'INR',? from report_submissions where id=?",
        UUID.randomUUID(),
        id,
        submission,
        actor.id(),
        normalize(body.reference()),
        Timestamp.from(clock.instant()),
        submission);
    probe.reached("reimbursement");
    workflow.transition(
        r, actor, ReportState.REIMBURSEMENT_RECORDED, "REIMBURSEMENT_RECORDED", null);
    return new Result(views.reimbursement(id), r.revision);
  }
}
