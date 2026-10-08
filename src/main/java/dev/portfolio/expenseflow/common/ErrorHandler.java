package dev.portfolio.expenseflow.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

  private ResponseEntity<ApiError> error(
      int status,
      String code,
      String message,
      List<ApiError.FieldError> fields,
      HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(new ApiError(code, message, RequestIds.id(request), fields));
  }

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ApiError> domain(DomainException e, HttpServletRequest r) {
    return error(e.status, e.code, e.getMessage(), e.fields, r);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    return error(
        400,
        "VALIDATION_FAILED",
        "Invalid request fields.",
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
            .toList(),
        r);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingRequestHeaderException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest r) {
    return error(400, "INVALID_REQUEST", "Invalid request.", List.of(), r);
  }

  @ExceptionHandler({
    CannotAcquireLockException.class,
    QueryTimeoutException.class,
    TransientDataAccessException.class
  })
  ResponseEntity<ApiError> transientFailure(Exception e, HttpServletRequest r) {
    return error(
        503,
        "RETRYABLE_CONFLICT",
        "Database contention; fetch current state before retrying.",
        List.of(),
        r);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> constraint(DataIntegrityViolationException e, HttpServletRequest r) {
    String diagnostic = e.getMostSpecificCause().getMessage();
    String code =
        diagnostic != null && diagnostic.contains("reimbursement_reference_unique")
            ? "DUPLICATE_REIMBURSEMENT_REFERENCE"
            : "DATA_CONFLICT";
    return error(409, code, "The operation conflicts with an existing record.", List.of(), r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest r) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof java.sql.SQLException sql
          && java.util.Set.of("55P03", "57014", "40P01", "40001").contains(sql.getSQLState()))
        return transientFailure(e, r);
    }
    log.error(
        "requestId={} unexpected failureType={} stack={}",
        RequestIds.id(r),
        e.getClass().getName(),
        java.util.Arrays.toString(e.getStackTrace()));
    return error(500, "INTERNAL_ERROR", "Unexpected server failure.", List.of(), r);
  }
}
