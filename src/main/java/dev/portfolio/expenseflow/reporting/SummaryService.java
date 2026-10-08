package dev.portfolio.expenseflow.reporting;

import dev.portfolio.expenseflow.identity.Actor;
import dev.portfolio.expenseflow.policy.ExpensePolicy;
import dev.portfolio.expenseflow.report.ReportService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SummaryService {
  public record Bucket(String category, long count, BigDecimal amount) {}

  public record Summary(
      String currency,
      String countMeaning,
      LocalDate from,
      LocalDate to,
      List<Bucket> workflow,
      List<Bucket> reimbursements) {}

  private final JdbcTemplate jdbc;

  public SummaryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static Timestamp[] bounds(LocalDate from, LocalDate to) {
    long days = ChronoUnit.DAYS.between(from, to);
    if (days <= 0 || days > 366) throw new IllegalArgumentException("Range must be 1-366 days");
    return new Timestamp[] {
      Timestamp.from(from.atStartOfDay(ExpensePolicy.BUSINESS_ZONE).toInstant()),
      Timestamp.from(to.atStartOfDay(ExpensePolicy.BUSINESS_ZONE).toInstant())
    };
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Summary manager(Actor actor, LocalDate from, LocalDate to) {
    ReportService.role(actor, "MANAGER");
    var b = bounds(from, to);
    var rows =
        jdbc.query(
            """
with classified as (
  select s.total,coalesce((select case e.event_type when 'MANAGER_APPROVE' then 'MANAGER_APPROVED'
    when 'MANAGER_RETURN' then 'MANAGER_RETURNED' when 'MANAGER_REJECT' then 'MANAGER_REJECTED'
    when 'EMPLOYEE_WITHDRAW' then 'EMPLOYEE_WITHDRAWN' end from report_events e
    where e.submission_id=s.id and e.event_type in ('MANAGER_APPROVE','MANAGER_RETURN','MANAGER_REJECT','EMPLOYEE_WITHDRAW')
    order by e.created_at,e.id limit 1),'PENDING_MANAGER') category
  from report_submissions s where s.approver_id=? and s.submitted_at>=? and s.submitted_at<?)
select category,count(*) count, sum(total) amount from classified group by category order by category
""",
            (rs, n) ->
                new Bucket(
                    rs.getString("category"), rs.getLong("count"), rs.getBigDecimal("amount")),
            actor.id(),
            b[0],
            b[1]);
    return new Summary("INR", "Submission revisions", from, to, rows, List.of());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Summary finance(Actor actor, LocalDate from, LocalDate to) {
    ReportService.role(actor, "FINANCE");
    var b = bounds(from, to);
    var workflow =
        jdbc.query(
            "select r.state category,count(*) count,sum(s.total) amount from expense_reports r join"
                + " report_submissions s on s.report_id=r.id and"
                + " s.submission_number=r.current_submission_number where r.state in"
                + " ('APPROVED','FINANCE_ACCEPTED') and s.submitted_at>=? and s.submitted_at<?"
                + " group by r.state order by r.state",
            (rs, n) ->
                new Bucket(
                    rs.getString("category"), rs.getLong("count"), rs.getBigDecimal("amount")),
            b[0],
            b[1]);
    var reimbursements =
        jdbc.query(
            "select i.category,count(distinct r.id) count,sum(i.amount) amount from reimbursements"
                + " r join submitted_expense_items i on i.submission_id=r.submission_id where"
                + " r.recorded_at>=? and r.recorded_at<? group by i.category order by i.category",
            (rs, n) ->
                new Bucket(
                    rs.getString("category"), rs.getLong("count"), rs.getBigDecimal("amount")),
            b[0],
            b[1]);
    return new Summary(
        "INR", "Current reports; reimbursements per category", from, to, workflow, reimbursements);
  }
}
