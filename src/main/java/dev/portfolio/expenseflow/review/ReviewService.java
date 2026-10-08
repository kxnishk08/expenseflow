package dev.portfolio.expenseflow.review;

import dev.portfolio.expenseflow.common.*;
import dev.portfolio.expenseflow.identity.Actor;
import dev.portfolio.expenseflow.report.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReviewService {
  private final ReportService workflow;
  private final ReportRepository reports;
  private final ReportViews views;

  public ReviewService(ReportService workflow, ReportRepository reports, ReportViews views) {
    this.workflow = workflow;
    this.reports = reports;
    this.views = views;
  }

  public void manager(Actor actor, ExpenseReport r) {
    ReportService.role(actor, "MANAGER");
    if (r.currentSubmissionNumber == 0
        || !workflow.approver(r).equals(actor.id())
        || r.ownerId.equals(actor.id())) throw DomainException.missing();
  }

  public static void finance(Actor actor, ExpenseReport r) {
    ReportService.role(actor, "FINANCE");
    if (!Set.of(
            ReportState.APPROVED, ReportState.FINANCE_ACCEPTED, ReportState.REIMBURSEMENT_RECORDED)
        .contains(r.state)) throw DomainException.missing();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReportViews.Reviewer managerRead(Actor actor, UUID id, boolean history) {
    ReportService.role(actor, "MANAGER");
    var r = reports.findById(id).orElseThrow(DomainException::missing);
    if (!history) manager(actor, r);
    return views.reviewer(r, actor.id(), null);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReportViews.Reviewer financeRead(Actor actor, UUID id, boolean audit) {
    ReportService.role(actor, "FINANCE");
    var r = reports.findById(id).orElseThrow(DomainException::missing);
    if (audit) return views.reviewer(r, null, actor.id());
    finance(actor, r);
    // Finance current workflow exposes just the current immutable submission and its events.
    var all = views.reviewer(r, null, null);
    var current =
        all.submissions().stream()
            .filter(s -> s.submissionNumber() == r.currentSubmissionNumber)
            .toList();
    var sid = current.getFirst().id();
    return new ReportViews.Reviewer(
        r.id,
        r.state,
        r.revision,
        current,
        all.events().stream().filter(e -> sid.equals(e.submissionId())).toList(),
        all.reimbursement());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<ReportViews.Brief> queue(
      Actor actor, boolean finance, ReportState state, int page, int size) {
    ReportService.role(actor, finance ? "FINANCE" : "MANAGER");
    if (finance) {
      if (state != null && state != ReportState.APPROVED && state != ReportState.FINANCE_ACCEPTED)
        throw new IllegalArgumentException("Invalid finance state");
      return views.list(
          state == null ? "where r.state in ('APPROVED','FINANCE_ACCEPTED')" : "where r.state=?",
          state == null ? List.of() : List.of(state.name()),
          page,
          size);
    }
    return views.list(
        "where r.state='SUBMITTED' and exists(select 1 from report_submissions s where"
            + " s.report_id=r.id and s.submission_number=r.current_submission_number and"
            + " s.approver_id=?)",
        List.of(actor.id()),
        page,
        size);
  }

  @Transactional
  public ReportViews.Reviewer managerDecision(Actor actor, UUID id, ReportCommands.Decision body) {
    var r = workflow.locked(id);
    manager(actor, r);
    ReportService.revision(r, body.expectedRevision());
    ReportService.state(r, ReportState.SUBMITTED);
    ReportState target =
        switch (body.decision()) {
          case "APPROVE" -> ReportState.APPROVED;
          case "REJECT" -> ReportState.REJECTED;
          case "RETURN" -> ReportState.RETURNED;
          default -> throw new IllegalArgumentException("Invalid decision");
        };
    workflow.transition(
        r,
        actor,
        target,
        "MANAGER_" + body.decision(),
        ReportService.cleanComment(body.comment(), target != ReportState.APPROVED));
    return views.reviewer(r, actor.id(), null);
  }

  @Transactional
  public ReportViews.Reviewer financeDecision(Actor actor, UUID id, ReportCommands.Decision body) {
    var r = workflow.locked(id);
    finance(actor, r);
    ReportService.revision(r, body.expectedRevision());
    ReportService.state(r, ReportState.APPROVED);
    ReportState target =
        switch (body.decision()) {
          case "ACCEPT" -> ReportState.FINANCE_ACCEPTED;
          case "RETURN" -> ReportState.RETURNED;
          default -> throw new IllegalArgumentException("Invalid decision");
        };
    workflow.transition(
        r,
        actor,
        target,
        "FINANCE_" + body.decision(),
        ReportService.cleanComment(body.comment(), target == ReportState.RETURNED));
    return target == ReportState.RETURNED
        ? views.reviewer(r, null, actor.id())
        : financeRead(actor, id, false);
  }
}
