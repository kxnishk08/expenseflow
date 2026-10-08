package dev.portfolio.expenseflow.report;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<ExpenseItem, UUID> {
  List<ExpenseItem> findByReportIdOrderById(UUID reportId);

  Optional<ExpenseItem> findByIdAndReportId(UUID id, UUID reportId);
}
