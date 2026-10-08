package dev.portfolio.expenseflow.policy;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class PolicyController {
  private final ExpensePolicy policy;

  public PolicyController(ExpensePolicy policy) {
    this.policy = policy;
  }

  @GetMapping("/api/policies/current")
  Map<String, Object> current() {
    return Map.of(
        "policy",
        policy.current(),
        "currency",
        "INR",
        "maxItems",
        50,
        "maxTotal",
        "25000.00",
        "maxAgeDays",
        90,
        "businessZone",
        "Asia/Kolkata");
  }
}
