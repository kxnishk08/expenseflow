package dev.portfolio.expenseflow.report;

import dev.portfolio.expenseflow.common.PageResult;
import dev.portfolio.expenseflow.identity.Actor;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<ReportViews.Owner> create(
      @AuthenticationPrincipal Actor a, @Valid @RequestBody ReportCommands.Create b) {
    var r = service.create(a, b);
    return ResponseEntity.created(URI.create("/api/reports/" + r.id())).body(r);
  }

  @GetMapping("/mine")
  PageResult<ReportViews.Brief> mine(
      @AuthenticationPrincipal Actor a,
      @RequestParam(required = false) ReportState state,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.mine(a, state, page, size);
  }

  @GetMapping("/{id}")
  ReportViews.Owner get(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return service.get(a, id);
  }

  @PatchMapping("/{id}")
  ReportViews.Owner title(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Title b) {
    return service.title(a, id, b);
  }

  @PostMapping("/{id}/items")
  ResponseEntity<ReportService.ItemResult> add(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Item b) {
    var r = service.saveItem(a, id, null, b);
    return ResponseEntity.created(URI.create("/api/reports/" + id + "/items/" + r.item().id()))
        .body(r);
  }

  @PutMapping("/{id}/items/{itemId}")
  ReportService.ItemResult edit(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @Valid @RequestBody ReportCommands.Item b) {
    return service.saveItem(a, id, itemId, b);
  }

  @DeleteMapping("/{id}/items/{itemId}")
  Map<String, Object> delete(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @RequestParam long expectedRevision) {
    return service.deleteItem(a, id, itemId, expectedRevision);
  }

  @PostMapping("/{id}/submit")
  ReportViews.Owner submit(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Revision b) {
    return service.submit(a, id, b.expectedRevision());
  }

  @PostMapping("/{id}/withdraw")
  ReportViews.Owner withdraw(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Withdraw b) {
    return service.withdraw(a, id, b);
  }

  @PostMapping("/{id}/reopen")
  ReportViews.Owner reopen(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody ReportCommands.Revision b) {
    return service.reopen(a, id, b.expectedRevision());
  }

  @GetMapping("/{id}/history")
  PageResult<ReportViews.Event> history(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(a, id, page, size);
  }
}
