package dev.portfolio.expenseflow.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionLimits {
  private final JdbcTemplate jdbc;

  public TransactionLimits(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void apply() {
    jdbc.execute("SET LOCAL lock_timeout='3s'");
    jdbc.execute("SET LOCAL statement_timeout='10s'");
  }
}
