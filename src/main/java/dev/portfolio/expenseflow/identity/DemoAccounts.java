package dev.portfolio.expenseflow.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DemoAccounts implements ApplicationRunner {
  private final AccountRepository accounts;
  private final PasswordEncoder passwords;
  private final Environment env;
  private final Clock clock;

  public DemoAccounts(
      AccountRepository accounts, PasswordEncoder passwords, Environment env, Clock clock) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.env = env;
    this.clock = clock;
  }

  @Transactional
  public void run(org.springframework.boot.ApplicationArguments args) {
    if (!env.getProperty("DEMO_PROVISION", "false").equals("true")) return;
    UUID m1 = provision("m1", "MANAGER", null), m2 = provision("m2", "MANAGER", null);
    provision("alice", "EMPLOYEE", m1);
    provision("bob", "EMPLOYEE", m2);
    provision("f1", "FINANCE", null);
  }

  private UUID provision(String name, String role, UUID manager) {
    String password =
        env.getRequiredProperty("DEMO_" + name.toUpperCase(Locale.ROOT) + "_PASSWORD");
    validatePassword(password);
    return accounts
        .findByEmail(name + "@example.test")
        .map(a -> a.id)
        .orElseGet(
            () ->
                accounts.save(
                        new Account(
                            UUID.randomUUID(),
                            name + "@example.test",
                            name,
                            passwords.encode(password),
                            role,
                            manager,
                            clock.instant()))
                    .id);
  }

  public static void validatePassword(String password) {
    if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Password must be at least 12 characters and at most 72 UTF-8 bytes.");
  }
}
