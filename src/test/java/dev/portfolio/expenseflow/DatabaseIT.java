package dev.portfolio.expenseflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseIT extends DatabaseSupport {
  @Autowired JdbcTemplate jdbc;

  @Test
  void migrationsCreatePolicyOnRealPostgres() {
    assertThat(jdbc.queryForObject("select version()", String.class)).contains("PostgreSQL");
    assertThat(jdbc.queryForObject("select count(*) from policy_category_limits", Integer.class))
        .isEqualTo(3);
  }
}
