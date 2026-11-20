package com.taskflow.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class ExceptionHandlerAdvice {
  @ExceptionHandler(
      org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
  void disconnected(Exception e) {
    log.debug("Client disconnected before the response completed");
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException e) {
    return ResponseEntity.status(e.getStatus())
        .body(ApiError.of(e.getStatus().value(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
    var errors = new LinkedHashMap<String, String>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(new ApiError(400, "Please check the highlighted fields", errors, Instant.now()));
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<ApiError> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(ApiError.of(400, "Invalid request. Check field types and values."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> denied() {
    return ResponseEntity.status(403).body(ApiError.of(403, "Access denied"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict() {
    return ResponseEntity.status(409)
        .body(ApiError.of(409, "This record already exists or is still in use"));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ApiError> stale() {
    return ResponseEntity.status(409)
        .body(ApiError.of(409, "This record changed. Refresh and try again."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> tooLarge() {
    return ResponseEntity.status(413)
        .body(ApiError.of(413, "Attachments must be 10 MB or smaller"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e) {
    log.error("Unhandled API error", e);
    return ResponseEntity.internalServerError()
        .body(ApiError.of(500, "Something went wrong. Please try again."));
  }
}
