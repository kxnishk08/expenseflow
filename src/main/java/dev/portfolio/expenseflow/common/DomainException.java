package dev.portfolio.expenseflow.common;

import java.util.List;

public class DomainException extends RuntimeException {
  public final int status;
  public final String code;
  public final List<ApiError.FieldError> fields;

  public DomainException(int status, String code, String message) {
    this(status, code, message, List.of());
  }

  public DomainException(
      int status, String code, String message, List<ApiError.FieldError> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = List.copyOf(fields);
  }

  public static DomainException missing() {
    return new DomainException(404, "NOT_FOUND", "Record not found.");
  }

  public static DomainException conflict(String code, String message) {
    return new DomainException(409, code, message);
  }
}
