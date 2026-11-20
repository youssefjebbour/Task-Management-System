package com.taskflow.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static ApiException notFound(String type) {
    return new ApiException(HttpStatus.NOT_FOUND, type + " not found");
  }

  public static ApiException forbidden() {
    return new ApiException(
        HttpStatus.FORBIDDEN, "You don't have permission to perform this action");
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, message);
  }
}
