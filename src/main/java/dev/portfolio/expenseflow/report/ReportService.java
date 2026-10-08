package dev.portfolio.expenseflow.report;

import dev.portfolio.expenseflow.common.*;
import dev.portfolio.expenseflow.identity.*;
import dev.portfolio.expenseflow.policy.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReportService {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ReportService.class);
  private final ReportRepository reports;
  private final ItemRepository items;
  private final AccountRepository accounts;
  private final JdbcTemplate jdbc;
  private final ReportViews views;
  private final ExpensePolicy policy;
  private final TransactionLimits limits;
  private final OperationProbe probe;
  private final Clock clock;

  public ReportService(
      ReportRepository reports,
      ItemRepository items,
      AccountRepository accounts,
      JdbcTemplate jdbc,
      ReportViews views,
      ExpensePolicy policy,
      TransactionLimits limits,
      OperationProbe probe,
      Clock clock) {
    this.reports = reports;
    this.items = items;
    this.accounts = accounts;
    this.jdbc = jdbc;
    this.views = views;
    this.policy = policy;
    this.limits = limits;
    this.probe = probe;
    this.clock = clock;
  }

  public static void role(Actor actor, String role) {
    if (!actor.role().equals(role))
      throw new DomainException(403, "FORBIDDEN", "Role cannot perform this operation.");
  }

  public ExpenseReport locked(UUID id) {
    limits.apply();
    return reports.lock(id).orElseThrow(DomainException::missing);
  }

  public static void revision(ExpenseReport r, long expected) {
    if (expected < 0) throw new IllegalArgumentException("Negative revision");
    if (r.revision != expected)
      throw DomainException.conflict(
          "STALE_REPORT_REVISION", "Report changed; reload it before deciding.");
  }

  public static void state(ExpenseReport r, ReportState state) {
    if (r.state != state)
      throw DomainException.conflict(
          "INVALID_REPORT_STATE", "Operation is unavailable in current report state.");
  }

  private void own(ExpenseReport r, Actor actor) {
    role(actor, "EMPLOYEE");
    if (!r.ownerId.equals(actor.id())) throw DomainException.missing();
  }

  private ExpenseReport draft(Actor actor, UUID id, long expected) {
    ExpenseReport r = locked(id);
    own(r, actor);
    revision(r, expected);
    state(r, ReportState.DRAFT);
    return r;
  }

  public UUID submission(ExpenseReport r) {
    return jdbc.queryForObject(
        "select id from report_submissions where report_id=? and submission_number=?",
        UUID.class,
        r.id,
        r.currentSubmissionNumber);
  }

  public UUID approver(ExpenseReport r) {
    return jdbc.queryForObject(
        "select approver_id from report_submissions where report_id=? and submission_number=?",
        UUID.class,
        r.id,
        r.currentSubmissionNumber);
  }

  public void transition(
      ExpenseReport r, Actor actor, ReportState to, String type, String comment) {
    String from = r.state.name();
    r.state = to;
    r.touch(clock.instant());
    reports.flush();
    event(
        r,
        actor.id(),
        r.currentSubmissionNumber == 0 ? null : submission(r),
        type,
        from,
        to.name(),
        comment);
    log.info(
        "reportId={} actorId={} operation={} from={} to={} revision={}",
        r.id,
        actor.id(),
        type,
        from,
        to,
        r.revision);
  }

  private void event(
      ExpenseReport r,
      UUID actor,
      UUID submission,
      String type,
      String from,
      String to,
      String comment) {
    jdbc.update(
        "insert into"
            + " report_events(id,report_id,submission_id,actor_id,event_type,from_state,to_state,comment,created_at)"
            + " values(?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        r.id,
        submission,
        actor,
        type,
        from,
        to,
        comment,
        Timestamp.from(clock.instant()));
    probe.reached("event");
  }

  @Transactional
  public ReportViews.Owner create(Actor actor, ReportCommands.Create body) {
    role(actor, "EMPLOYEE");
    limits.apply();
    jdbc.queryForObject(
        "select id from users where id=? for no key update", UUID.class, actor.id());
    if (jdbc.queryForObject(
            "select count(*) from expense_reports where owner_id=? and state in"
                + " ('DRAFT','RETURNED')",
            Long.class,
            actor.id())
        >= 10)
      throw DomainException.conflict(
          "DRAFT_LIMIT_REACHED", "At most ten draft/returned reports may be admitted.");
    var r =
        reports.saveAndFlush(new ExpenseReport(actor.id(), body.title().trim(), clock.instant()));
    event(r, actor.id(), null, "CREATED", null, "DRAFT", null);
    return views.owner(r);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReportViews.Owner get(Actor actor, UUID id) {
    var r = reports.findById(id).orElseThrow(DomainException::missing);
    own(r, actor);
    return views.owner(r);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<ReportViews.Brief> mine(Actor actor, ReportState state, int page, int size) {
    role(actor, "EMPLOYEE");
    List<Object> args = new ArrayList<>(List.of(actor.id()));
    String where = "where r.owner_id=?";
    if (state != null) {
      where += " and r.state=?";
      args.add(state.name());
    }
    return views.list(where, args, page, size);
  }

  @Transactional
  public ReportViews.Owner title(Actor actor, UUID id, ReportCommands.Title body) {
    var r = draft(actor, id, body.expectedRevision());
    r.title = body.title().trim();
    r.touch(clock.instant());
    reports.flush();
    return views.owner(r);
  }

  public record ItemResult(ReportViews.Item item, long reportRevision) {}

  @Transactional
  public ItemResult saveItem(Actor actor, UUID id, UUID itemId, ReportCommands.Item body) {
    var r = draft(actor, id, body.expectedRevision());
    var item =
        itemId == null
            ? new ExpenseItem(id)
            : items.findByIdAndReportId(itemId, id).orElseThrow(DomainException::missing);
    if (itemId == null && items.findByReportIdOrderById(id).size() >= 50)
      throw DomainException.conflict("POLICY_VIOLATION", "At most fifty items per report.");
    item.replace(body);
    items.saveAndFlush(item);
    r.touch(clock.instant());
    reports.flush();
    return new ItemResult(
        new ReportViews.Item(
            item.id,
            item.category.name(),
            item.expenseDate,
            item.merchant,
            item.description,
            item.amount,
            item.receiptReference,
            null,
            null),
        r.revision);
  }

  @Transactional
  public Map<String, Object> deleteItem(Actor actor, UUID id, UUID itemId, long expected) {
    var r = draft(actor, id, expected);
    items.delete(items.findByIdAndReportId(itemId, id).orElseThrow(DomainException::missing));
    items.flush();
    r.touch(clock.instant());
    reports.flush();
    return Map.of("reportId", id, "revision", r.revision);
  }

  @Transactional
  public ReportViews.Owner submit(Actor actor, UUID id, long expected) {
    var r = draft(actor, id, expected);
    Instant now = clock.instant();
    var owner = accounts.findById(actor.id()).orElseThrow(DomainException::missing);
    var manager = owner.managerId == null ? null : accounts.findById(owner.managerId).orElse(null);
    if (manager == null
        || !manager.active
        || !manager.role.equals("MANAGER")
        || manager.id.equals(actor.id()))
      throw DomainException.conflict(
          "NO_ASSIGNED_MANAGER", "An active assigned manager is required.");
    var version = policy.current();
    var draft = items.findByReportIdOrderById(id);
    var total = ExpensePolicy.validate(draft, version, now);
    UUID submission = UUID.randomUUID();
    r.currentSubmissionNumber++;
    jdbc.update(
        "insert into"
            + " report_submissions(id,report_id,submission_number,approver_id,policy_id,title_snapshot,total,submitted_at)"
            + " values(?,?,?,?,?,?,?,?)",
        submission,
        id,
        r.currentSubmissionNumber,
        manager.id,
        version.id(),
        r.title,
        total,
        Timestamp.from(now));
    for (var item : draft) {
      var limit = version.limits().get(item.category);
      jdbc.update(
          "insert into"
              + " submitted_expense_items(id,submission_id,source_item_id,category,expense_date,merchant,description,amount,receipt_reference,applied_cap,receipt_required)"
              + " values(?,?,?,?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          submission,
          item.id,
          item.category.name(),
          item.expenseDate,
          item.merchant,
          item.description,
          item.amount,
          item.receiptReference,
          limit.cap(),
          limit.receiptRequired());
    }
    probe.reached("snapshot");
    transition(r, actor, ReportState.SUBMITTED, "SUBMITTED", null);
    return views.owner(r);
  }

  @Transactional
  public ReportViews.Owner withdraw(Actor actor, UUID id, ReportCommands.Withdraw body) {
    var r = locked(id);
    own(r, actor);
    revision(r, body.expectedRevision());
    state(r, ReportState.SUBMITTED);
    transition(
        r, actor, ReportState.WITHDRAWN, "EMPLOYEE_WITHDRAW", cleanComment(body.comment(), false));
    return views.owner(r);
  }

  @Transactional
  public ReportViews.Owner reopen(Actor actor, UUID id, long expected) {
    var r = locked(id);
    own(r, actor);
    revision(r, expected);
    state(r, ReportState.RETURNED);
    transition(r, actor, ReportState.DRAFT, "REOPENED", null);
    return views.owner(r);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<ReportViews.Event> history(Actor actor, UUID id, int page, int size) {
    var r = reports.findById(id).orElseThrow(DomainException::missing);
    own(r, actor);
    PageResult.validate(page, size);
    // Event retrieval has a database pagination boundary, including count.
    long count =
        jdbc.queryForObject("select count(*) from report_events where report_id=?", Long.class, id);
    var result =
        jdbc.query(
            "select * from report_events where report_id=? order by created_at,id limit ? offset ?",
            (rs, n) ->
                new ReportViews.Event(
                    rs.getObject("id", UUID.class),
                    rs.getObject("submission_id", UUID.class),
                    rs.getObject("actor_id", UUID.class),
                    rs.getString("event_type"),
                    rs.getString("from_state"),
                    rs.getString("to_state"),
                    rs.getString("comment"),
                    rs.getTimestamp("created_at").toInstant()),
            id,
            size,
            (long) page * size);
    return new PageResult<>(result, page, size, count);
  }

  public static String cleanComment(String comment, boolean required) {
    String value = comment == null ? null : comment.trim();
    if (value != null && value.length() > 1000)
      throw new IllegalArgumentException("Comment too long");
    if (required && (value == null || value.isEmpty()))
      throw new DomainException(
          400,
          "VALIDATION_FAILED",
          "A return/reject requires a comment.",
          List.of(new ApiError.FieldError("comment", "Required for return/reject.")));
    return value == null || value.isEmpty() ? null : value;
  }
}
