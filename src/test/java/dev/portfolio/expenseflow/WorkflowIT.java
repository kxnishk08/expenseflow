package dev.portfolio.expenseflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.portfolio.expenseflow.common.*;
import dev.portfolio.expenseflow.identity.*;
import dev.portfolio.expenseflow.policy.Category;
import dev.portfolio.expenseflow.reimbursement.ReimbursementService;
import dev.portfolio.expenseflow.report.*;
import dev.portfolio.expenseflow.reporting.SummaryService;
import dev.portfolio.expenseflow.review.ReviewService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WorkflowIT.TimeConfig.class)
class WorkflowIT extends DatabaseSupport {
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-09T06:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired ReportService reports;
  @Autowired ReviewService reviews;
  @Autowired ReimbursementService reimbursements;
  @Autowired SummaryService summaries;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccountRepository accounts;
  @Autowired PasswordEncoder passwords;
  @Autowired MockMvc mvc;
  @MockitoBean OperationProbe probe;
  Actor alice, bob, m1, m2, f1, f2;

  @BeforeEach
  void setup() {
    reset(probe);
    jdbc.execute("truncate users cascade");
    m1 = actor("m1", "MANAGER", null);
    m2 = actor("m2", "MANAGER", null);
    alice = actor("alice", "EMPLOYEE", m1.id());
    bob = actor("bob", "EMPLOYEE", m2.id());
    f1 = actor("f1", "FINANCE", null);
    f2 = actor("f2", "FINANCE", null);
  }

  Actor actor(String name, String role, UUID manager) {
    var a =
        accounts.saveAndFlush(
            new Account(
                UUID.randomUUID(),
                name + "@example.test",
                name,
                passwords.encode("test-password-123"),
                role,
                manager,
                Instant.now()));
    return new Actor(a.id, a.email, a.displayName, a.passwordHash, a.role, a.active);
  }

  ReportViews.Owner create() {
    return reports.create(alice, new ReportCommands.Create("Travel claim"));
  }

  ReportCommands.Item item(long revision, Category category, String amount) {
    return new ReportCommands.Item(
        revision,
        category,
        LocalDate.of(2026, 10, 9),
        "Merchant",
        "Original description",
        new BigDecimal(amount),
        "R-001");
  }

  ReportViews.Owner submitted() {
    var r = create();
    reports.saveItem(alice, r.id(), null, item(0, Category.TRAVEL, "600.00"));
    reports.saveItem(alice, r.id(), null, item(1, Category.MEALS, "450.00"));
    return reports.submit(alice, r.id(), 2);
  }

  ReportViews.Owner accepted() {
    var r = submitted();
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "APPROVE", null));
    reviews.financeDecision(f1, r.id(), new ReportCommands.Decision(4L, "ACCEPT", null));
    return reports.get(alice, r.id());
  }

  long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  @Test
  void completeReturnResubmitApproveFinanceReimburseHistoryAndSummary() {
    var r = submitted();
    assertThat(r.submissions().getFirst().total()).isEqualByComparingTo("1050");
    reviews.managerDecision(
        m1, r.id(), new ReportCommands.Decision(3L, "RETURN", "Explain travel"));
    reports.reopen(alice, r.id(), 4);
    var current = reports.get(alice, r.id());
    UUID item = current.items().getFirst().id();
    var changed =
        new ReportCommands.Item(
            5L,
            Category.TRAVEL,
            LocalDate.of(2026, 10, 9),
            "Merchant",
            "Corrected",
            new BigDecimal("600"),
            "R-001");
    // UUID order is not category order: replace the TRAVEL item explicitly.
    item =
        current.items().stream()
            .filter(i -> i.category().equals("TRAVEL"))
            .findFirst()
            .orElseThrow()
            .id();
    reports.saveItem(alice, r.id(), item, changed);
    var resubmitted = reports.submit(alice, r.id(), 6);
    assertThat(resubmitted.currentSubmissionNumber()).isEqualTo(2);
    assertThat(resubmitted.submissions().getFirst().items())
        .anySatisfy(i -> assertThat(i.description()).isEqualTo("Original description"));
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(7L, "APPROVE", null));
    reviews.financeDecision(f1, r.id(), new ReportCommands.Decision(8L, "ACCEPT", null));
    var payment =
        reimbursements.record(f1, r.id(), new ReportCommands.Reimbursement(9L, " demo-ref-0001 "));
    assertThat(payment.reimbursement().amount()).isEqualByComparingTo("1050");
    assertThat(payment.reportRevision()).isEqualTo(10);
    assertThatThrownBy(
            () ->
                reimbursements.record(
                    f1, r.id(), new ReportCommands.Reimbursement(9L, "demo-ref-0001")))
        .isInstanceOf(DomainException.class);
    assertThat(count("reimbursements")).isEqualTo(1);
    var manager = summaries.manager(m1, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10));
    assertThat(manager.workflow())
        .contains(
            new SummaryService.Bucket("MANAGER_RETURNED", 1, new BigDecimal("1050.00")),
            new SummaryService.Bucket("MANAGER_APPROVED", 1, new BigDecimal("1050.00")));
    var finance = summaries.finance(f1, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10));
    assertThat(finance.workflow()).isEmpty();
    assertThat(finance.reimbursements())
        .contains(
            new SummaryService.Bucket("TRAVEL", 1, new BigDecimal("600.00")),
            new SummaryService.Bucket("MEALS", 1, new BigDecimal("450.00")));
    assertThat(count("report_events")).isEqualTo(8);
  }

  @Test
  void foreignOwnerAndItemAndUnassignedManagerAreHidden() throws Exception {
    var a = submitted();
    var b = reports.create(bob, new ReportCommands.Create("Bob"));
    var foreign = reports.saveItem(bob, b.id(), null, item(0, Category.MEALS, "50"));
    mvc.perform(get("/api/reports/" + a.id()).with(user(bob))).andExpect(status().isNotFound());
    mvc.perform(get("/api/manager/reports/" + a.id()).with(user(m2)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/manager/reports/" + a.id() + "/decision")
                .with(user(m2))
                .with(csrf())
                .contentType("application/json")
                .content("{\"expectedRevision\":3,\"decision\":\"APPROVE\"}"))
        .andExpect(status().isNotFound());
    var draft = create();
    assertThatThrownBy(
            () ->
                reports.saveItem(
                    alice, draft.id(), foreign.item().id(), item(0, Category.TRAVEL, "100")))
        .isInstanceOf(DomainException.class);
    assertThat(reports.get(alice, draft.id()).revision()).isZero();
  }

  @Test
  void financeDraftAndReviewerMutableContentAreHidden() throws Exception {
    var r = submitted();
    mvc.perform(get("/api/finance/reports/" + r.id()).with(user(f1)))
        .andExpect(status().isNotFound());
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "RETURN", "Clarify"));
    reports.reopen(alice, r.id(), 4);
    reports.title(alice, r.id(), new ReportCommands.Title(5L, "Secret draft title"));
    mvc.perform(get("/api/manager/reports/" + r.id() + "/submissions").with(user(m1)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Secret draft title"))));
  }

  @Test
  void sessionLoginCsrfLogoutAndInvalidCredentials() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .param("username", alice.email())
                .param("password", "bad")
                .with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    mvc.perform(
            post("/api/auth/login")
                .param("username", alice.email())
                .param("password", "test-password-123"))
        .andExpect(status().isForbidden());
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("username", alice.email())
                    .param("password", "test-password-123"))
            .andExpect(status().isNoContent())
            .andReturn();
    var session =
        (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
    mvc.perform(get("/api/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("EMPLOYEE"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"items", "submit", "withdraw", "reopen"})
  void mutationsRequireCsrf(String suffix) throws Exception {
    var r = create();
    mvc.perform(
            post("/api/reports/" + r.id() + "/" + suffix)
                .with(user(alice))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.requestId").exists());
  }

  @Test
  void badShapesUnknownFieldsPrecisionMissingRevisionAndRoleInjection() throws Exception {
    mvc.perform(
            post("/api/reports")
                .with(user(alice))
                .with(csrf())
                .contentType("application/json")
                .content("{\"title\":\"x\",\"ownerId\":\"bad\"}"))
        .andExpect(status().isBadRequest());
    var r = create();
    mvc.perform(
            post("/api/reports/" + r.id() + "/submit")
                .with(user(alice))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/reports")
                .with(user(m1))
                .with(csrf())
                .contentType("application/json")
                .content("{\"title\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/reports/" + r.id() + "/items")
                .with(user(alice))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"expectedRevision\":0,\"category\":\"TRAVEL\",\"expenseDate\":\"2026-10-09\",\"merchant\":\"x\",\"description\":\"x\",\"amount\":1.001,\"receiptReference\":\"x\"}"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "overcap", "old", "future"})
  void invalidSubmissionDoesNotCreateSnapshotOrEvent(String type) {
    var r = create();
    long revision = 0;
    if (!type.equals("empty")) {
      var i = item(0, Category.TRAVEL, type.equals("overcap") ? "2000.01" : "100");
      if (type.equals("old") || type.equals("future"))
        i =
            new ReportCommands.Item(
                0L,
                i.category(),
                type.equals("old") ? LocalDate.of(2026, 7, 10) : LocalDate.of(2026, 10, 10),
                i.merchant(),
                i.description(),
                i.amount(),
                i.receiptReference());
      reports.saveItem(alice, r.id(), null, i);
      revision = 1;
    }
    long expected = revision;
    assertThatThrownBy(() -> reports.submit(alice, r.id(), expected))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("POLICY_VIOLATION");
    assertThat(count("report_submissions")).isZero();
    assertThat(count("report_events")).isEqualTo(1);
    assertThat(reports.get(alice, r.id()).state()).isEqualTo(ReportState.DRAFT);
  }

  @Test
  void immutableSubmittedAndApprovedItemsCannotEdit() {
    var r = submitted();
    assertThatThrownBy(
            () ->
                reports.saveItem(
                    alice, r.id(), r.items().getFirst().id(), item(3, Category.TRAVEL, "10")))
        .isInstanceOf(DomainException.class);
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "APPROVE", null));
    assertThatThrownBy(() -> reports.title(alice, r.id(), new ReportCommands.Title(4L, "Changed")))
        .isInstanceOf(DomainException.class);
    assertThat(reports.get(alice, r.id()).submissions().getFirst().total())
        .isEqualByComparingTo("1050");
  }

  @Test
  void duplicateReferenceOnDifferentReportRollsBackAndStaleFinanceCannotAct() {
    var first = accepted();
    reimbursements.record(f1, first.id(), new ReportCommands.Reimbursement(5L, "UNIQUE-REF-01"));
    var second = accepted();
    assertThatThrownBy(
            () ->
                reimbursements.record(
                    f1, second.id(), new ReportCommands.Reimbursement(5L, "unique-ref-01")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(reports.get(alice, second.id()).state()).isEqualTo(ReportState.FINANCE_ACCEPTED);
    assertThat(reports.get(alice, second.id()).revision()).isEqualTo(5);
    assertThat(count("reimbursements")).isEqualTo(1);
    assertThatThrownBy(
            () ->
                reviews.financeDecision(
                    f1, second.id(), new ReportCommands.Decision(4L, "RETURN", "Return")))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void financeReturnAuditIsActorScopedAndContainsOnlySnapshots() {
    var r = submitted();
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "APPROVE", null));
    reviews.financeDecision(
        f1, r.id(), new ReportCommands.Decision(4L, "RETURN", "Receipt unclear"));
    reports.reopen(alice, r.id(), 5);
    reports.title(alice, r.id(), new ReportCommands.Title(6L, "Private draft"));
    assertThat(reviews.financeRead(f1, r.id(), true).submissions().getFirst().title())
        .isEqualTo("Travel claim");
    assertThatThrownBy(() -> reviews.financeRead(f2, r.id(), true))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> reviews.financeRead(f1, r.id(), false))
        .isInstanceOf(DomainException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"snapshot", "event", "reimbursement"})
  void injectedFailureRollsBackCompleteOperation(String point) {
    var r = point.equals("reimbursement") ? accepted() : create();
    if (!point.equals("reimbursement"))
      reports.saveItem(alice, r.id(), null, item(0, Category.TRAVEL, "100"));
    long events = count("report_events");
    doThrow(new IllegalStateException("controlled test failure")).when(probe).reached(point);
    if (point.equals("reimbursement"))
      assertThatThrownBy(
              () ->
                  reimbursements.record(
                      f1, r.id(), new ReportCommands.Reimbursement(5L, "ROLLBACK-REF")))
          .isInstanceOf(IllegalStateException.class);
    else
      assertThatThrownBy(() -> reports.submit(alice, r.id(), 1))
          .isInstanceOf(IllegalStateException.class);
    assertThat(count("report_events")).isEqualTo(events);
    assertThat(count("reimbursements")).isZero();
    assertThat(reports.get(alice, r.id()).revision())
        .isEqualTo(point.equals("reimbursement") ? 5 : 1);
    if (!point.equals("reimbursement")) assertThat(count("report_submissions")).isZero();
  }

  List<String> race(Callable<?> one, Callable<?> two) throws Exception {
    var gate = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Future<String>> futures = new ArrayList<>();
      for (var task : List.of(one, two))
        futures.add(
            pool.submit(
                () -> {
                  gate.await(5, TimeUnit.SECONDS);
                  try {
                    task.call();
                    return "OK";
                  } catch (DomainException e) {
                    return e.code;
                  }
                }));
      return List.of(
          futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
    }
  }

  @Test
  void twoManagerDecisionsOneEvent() throws Exception {
    var r = submitted();
    long before = count("report_events");
    assertThat(
            race(
                () ->
                    reviews.managerDecision(
                        m1, r.id(), new ReportCommands.Decision(3L, "APPROVE", null)),
                () ->
                    reviews.managerDecision(
                        m1, r.id(), new ReportCommands.Decision(3L, "RETURN", "Fix"))))
        .containsExactlyInAnyOrder("OK", "STALE_REPORT_REVISION");
    assertThat(count("report_events")).isEqualTo(before + 1);
  }

  @Test
  void itemEditRacesSubmissionWithoutSilentSnapshotChange() throws Exception {
    var r = create();
    var i = reports.saveItem(alice, r.id(), null, item(0, Category.TRAVEL, "600"));
    assertThat(
            race(
                () ->
                    reports.saveItem(alice, r.id(), i.item().id(), item(1, Category.TRAVEL, "700")),
                () -> reports.submit(alice, r.id(), 1)))
        .containsExactlyInAnyOrder("OK", "STALE_REPORT_REVISION");
    var after = reports.get(alice, r.id());
    if (after.state() == ReportState.SUBMITTED)
      assertThat(after.submissions().getFirst().total()).isEqualByComparingTo("600");
    else assertThat(after.items().getFirst().amount()).isEqualByComparingTo("700");
  }

  @Test
  void withdrawalRacesApprovalOneTransition() throws Exception {
    var r = submitted();
    long before = count("report_events");
    assertThat(
            race(
                () -> reports.withdraw(alice, r.id(), new ReportCommands.Withdraw(3L, null)),
                () ->
                    reviews.managerDecision(
                        m1, r.id(), new ReportCommands.Decision(3L, "APPROVE", null))))
        .containsExactlyInAnyOrder("OK", "STALE_REPORT_REVISION");
    assertThat(count("report_events")).isEqualTo(before + 1);
  }

  @Test
  void draftAdmissionIsSerialized() throws Exception {
    for (int n = 0; n < 9; n++) create();
    assertThat(race(this::create, this::create))
        .containsExactlyInAnyOrder("OK", "DRAFT_LIMIT_REACHED");
    assertThat(count("expense_reports")).isEqualTo(10);
  }

  @Test
  void rejectedAndWithdrawnReportsAreTerminalAndCommentsRequired() {
    var r = submitted();
    assertThatThrownBy(
            () ->
                reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "REJECT", " ")))
        .isInstanceOf(DomainException.class);
    reviews.managerDecision(
        m1, r.id(), new ReportCommands.Decision(3L, "REJECT", "Outside business purpose"));
    assertThatThrownBy(() -> reports.reopen(alice, r.id(), 4)).isInstanceOf(DomainException.class);
    var w = submitted();
    reports.withdraw(alice, w.id(), new ReportCommands.Withdraw(3L, "No longer needed"));
    assertThatThrownBy(() -> reports.reopen(alice, w.id(), 4)).isInstanceOf(DomainException.class);
  }

  @Test
  void draftItemDeletionPreservesHistoricalSnapshot() {
    var r = submitted();
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "RETURN", "Correct items"));
    reports.reopen(alice, r.id(), 4);
    reports.deleteItem(alice, r.id(), r.items().getFirst().id(), 5);
    var current = reports.get(alice, r.id());
    assertThat(current.items()).hasSize(1);
    assertThat(current.submissions().getFirst().items()).hasSize(2);
  }

  @Test
  void oldManagerCannotReadFutureReassignedSubmission() {
    var r = submitted();
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "RETURN", "Fix"));
    reports.reopen(alice, r.id(), 4);
    jdbc.update("update users set manager_id=? where id=?", m2.id(), alice.id());
    reports.submit(alice, r.id(), 5);
    assertThat(reviews.managerRead(m1, r.id(), true).submissions()).hasSize(1);
    assertThatThrownBy(() -> reviews.managerRead(m1, r.id(), false))
        .isInstanceOf(DomainException.class);
    assertThat(reviews.managerRead(m2, r.id(), false).submissions()).hasSize(1);
  }

  @Test
  void inactiveAssignedManagerPreventsSubmission() {
    var r = create();
    reports.saveItem(alice, r.id(), null, item(0, Category.TRAVEL, "100"));
    jdbc.update("update users set active=false where id=?", m1.id());
    assertThatThrownBy(() -> reports.submit(alice, r.id(), 1))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("NO_ASSIGNED_MANAGER");
    assertThat(count("report_submissions")).isZero();
  }

  @Test
  void duplicateConcurrentReimbursementCreatesOneEvent() throws Exception {
    var r = accepted();
    long events = count("report_events");
    assertThat(
            race(
                () ->
                    reimbursements.record(
                        f1, r.id(), new ReportCommands.Reimbursement(5L, "RACE-REFERENCE")),
                () ->
                    reimbursements.record(
                        f1, r.id(), new ReportCommands.Reimbursement(5L, "RACE-REFERENCE"))))
        .containsExactlyInAnyOrder("OK", "STALE_REPORT_REVISION");
    assertThat(count("reimbursements")).isEqualTo(1);
    assertThat(count("report_events")).isEqualTo(events + 1);
  }

  @Test
  void roleAndCsrfCoverReviewerAndItemMutationCategories() throws Exception {
    var r = accepted();
    String[] paths = {
      "/api/manager/reports/" + r.id() + "/decision",
      "/api/finance/reports/" + r.id() + "/decision",
      "/api/finance/reports/" + r.id() + "/reimbursement"
    };
    for (int n = 0; n < paths.length; n++) {
      Actor correct = n == 0 ? m1 : f1;
      mvc.perform(post(paths[n]).with(user(correct)).contentType("application/json").content("{}"))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(paths[n])
                  .with(user(alice))
                  .with(csrf())
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            patch("/api/reports/" + r.id())
                .with(user(alice))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/reports/" + r.id() + "/items/" + r.items().getFirst().id())
                .with(user(alice))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete(
                    "/api/reports/"
                        + r.id()
                        + "/items/"
                        + r.items().getFirst().id()
                        + "?expectedRevision=5")
                .with(user(alice)))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerSummaryIsHalfOpenAndPreservesEachSubmissionsDecision() {
    var r = submitted();
    reviews.managerDecision(m1, r.id(), new ReportCommands.Decision(3L, "APPROVE", null));
    reviews.financeDecision(f1, r.id(), new ReportCommands.Decision(4L, "RETURN", "Clarify"));
    reports.reopen(alice, r.id(), 5);
    reports.submit(alice, r.id(), 6);
    var summary = summaries.manager(m1, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10));
    assertThat(summary.workflow())
        .contains(
            new SummaryService.Bucket("MANAGER_APPROVED", 1, new BigDecimal("1050.00")),
            new SummaryService.Bucket("PENDING_MANAGER", 1, new BigDecimal("1050.00")));
    assertThat(
            summaries.manager(m2, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10)).workflow())
        .isEmpty();
    assertThat(
            summaries.manager(m1, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9)).workflow())
        .isEmpty();
  }

  @Test
  void admissionLockTimeoutReturns503AndNoPartialDraft() throws Exception {
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var manager =
        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
            java.util.Objects.requireNonNull(jdbc.getDataSource()));
    try (var pool = Executors.newSingleThreadExecutor()) {
      var blocker =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(manager)
                      .execute(
                          status -> {
                            jdbc.queryForObject(
                                "select id from users where id=? for no key update",
                                UUID.class,
                                alice.id());
                            locked.countDown();
                            try {
                              release.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(e);
                            }
                            return null;
                          }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        mvc.perform(
                post("/api/reports")
                    .with(user(alice))
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"title\":\"Blocked draft\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("RETRYABLE_CONFLICT"));
      } finally {
        release.countDown();
      }
      blocker.get(5, TimeUnit.SECONDS);
    }
    assertThat(count("expense_reports")).isZero();
    assertThat(count("report_events")).isZero();
  }

  @Test
  void inactiveLoginAndUnauthenticatedLogoutAreDenied() throws Exception {
    jdbc.update("update users set active=false where id=?", alice.id());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .param("username", alice.email())
                .param("password", "test-password-123"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isUnauthorized());
  }
}
