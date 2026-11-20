package com.taskflow.notification;

import com.taskflow.common.PageResponse;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {
  private final NotificationService service;

  public record Read(boolean read) {}

  @GetMapping
  PageResponse<NotificationService.View> list(
      @RequestParam(defaultValue = "false") boolean unread,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(unread, page, size);
  }

  @GetMapping("/unread-count")
  Map<String, Long> count() {
    return Map.of("count", service.count());
  }

  @PatchMapping("/{id}")
  NotificationService.View read(@PathVariable UUID id, @RequestBody Read r) {
    return service.read(id, r.read());
  }

  @PostMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void readAll() {
    service.readAll();
  }
}
