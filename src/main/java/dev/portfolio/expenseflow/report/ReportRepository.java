package dev.portfolio.expenseflow.report;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ReportRepository extends JpaRepository<ExpenseReport, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ExpenseReport r where r.id=:id")
  Optional<ExpenseReport> lock(UUID id);
}
