package dev.portfolio.expenseflow.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Intentionally public credentials for disposable portfolio identities, only in public-demo. */
@RestController
@Profile("public-demo")
public class DemoInfoController {
  private final Environment env;

  public DemoInfoController(Environment env) {
    this.env = env;
  }

  @GetMapping("/demo/config")
  ResponseEntity<Map<String, String>> config() {
    Map<String, String> accounts = new LinkedHashMap<>();
    for (String name : new String[] {"alice", "m1", "f1"})
      accounts.put(
          name + "@example.test",
          env.getRequiredProperty("DEMO_" + name.toUpperCase(java.util.Locale.ROOT) + "_PASSWORD"));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts);
  }
}
