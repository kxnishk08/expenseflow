package dev.portfolio.expenseflow.common;

import org.springframework.stereotype.Component;

/** No public failure endpoint. Tests replace this seam to prove transaction rollback. */
@Component
public class OperationProbe {
  public void reached(String point) {}
}
