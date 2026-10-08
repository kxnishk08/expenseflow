package dev.portfolio.expenseflow;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class DatabaseSupport {
  private static PostgreSQLContainer postgres;

  @DynamicPropertySource
  static synchronized void database(DynamicPropertyRegistry p) {
    String external = System.getenv("TEST_DB_URL");
    if (external == null) {
      if (postgres == null) {
        postgres = new PostgreSQLContainer("postgres:17.6-alpine");
        postgres.start();
      }
      p.add("spring.datasource.url", postgres::getJdbcUrl);
      p.add("spring.datasource.username", postgres::getUsername);
      p.add("spring.datasource.password", postgres::getPassword);
    } else {
      if (!external.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test"))
        throw new IllegalArgumentException(
            "TEST_DB_URL must name a dedicated *_test database; tests delete synthetic rows.");
      p.add("spring.datasource.url", () -> external);
      p.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
      p.add(
          "spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    }
  }
}
