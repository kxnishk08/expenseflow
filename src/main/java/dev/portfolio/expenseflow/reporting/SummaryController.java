package dev.portfolio.expenseflow.reporting;

import dev.portfolio.expenseflow.identity.Actor;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class SummaryController {
  private final SummaryService service;

  public SummaryController(SummaryService service) {
    this.service = service;
  }

  @GetMapping("/api/manager/summary")
  SummaryService.Summary manager(
      @AuthenticationPrincipal Actor a, @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return service.manager(a, from, to);
  }

  @GetMapping("/api/finance/summary")
  SummaryService.Summary finance(
      @AuthenticationPrincipal Actor a, @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return service.finance(a, from, to);
  }
}
