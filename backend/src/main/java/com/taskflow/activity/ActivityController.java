package com.taskflow.activity;

import com.taskflow.common.PageResponse;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
@Validated
public class ActivityController {
  private final ActivityService service;

  @GetMapping
  PageResponse<ActivityService.View> list(
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) UUID taskId,
      @RequestParam(defaultValue = "false") boolean audit,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(projectId, taskId, audit, page, size);
  }
}
