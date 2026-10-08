package dev.portfolio.expenseflow.review;

import dev.portfolio.expenseflow.common.PageResult;
import dev.portfolio.expenseflow.identity.Actor;
import dev.portfolio.expenseflow.reimbursement.ReimbursementService;
import dev.portfolio.expenseflow.report.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReviewController {
  private final ReviewService service;
  private final ReimbursementService reimbursements;

  public ReviewController(ReviewService service, ReimbursementService reimbursements) {
    this.service = service;
    this.reimbursements = reimbursements;
  }

  @GetMapping("/api/manager/reports")
  PageResult<ReportViews.Brief> managerQueue(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.queue(a, false, null, page, size);
  }

  @GetMapping("/api/finance/reports")
  PageResult<ReportViews.Brief> financeQueue(
      @AuthenticationPrincipal Actor a,
      @RequestParam(required = false) ReportState state,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.queue(a, true, state, page, size);
  }

  @GetMapping("/api/manager/reports/{id}")
  ReportViews.Reviewer manager(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return service.managerRead(a, id, false);
  }

  @GetMapping("/api/manager/reports/{id}/submissions")
  ReportViews.Reviewer managerHistory(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return service.managerRead(a, id, true);
  }

  @GetMapping("/api/finance/reports/{id}")
  ReportViews.Reviewer finance(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return service.financeRead(a, id, false);
  }

  @GetMapping("/api/finance/reports/{id}/returned-submissions")
  ReportViews.Reviewer financeHistory(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return service.financeRead(a, id, true);
  }

  @PostMapping("/api/manager/reports/{id}/decision")
  ReportViews.Reviewer managerDecision(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Decision b) {
    return service.managerDecision(a, id, b);
  }

  @PostMapping("/api/finance/reports/{id}/decision")
  ReportViews.Reviewer financeDecision(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Decision b) {
    return service.financeDecision(a, id, b);
  }

  @PostMapping("/api/finance/reports/{id}/reimbursement")
  ResponseEntity<ReimbursementService.Result> reimburse(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Reimbursement b) {
    var r = reimbursements.record(a, id, b);
    return ResponseEntity.created(URI.create("/api/finance/reports/" + id)).body(r);
  }
}
