package dev.portfolio.expenseflow.common;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
  public static void validate(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Invalid page/size");
  }
}
