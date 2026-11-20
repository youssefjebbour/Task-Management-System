package com.taskflow.report;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
  private final ReportService service;

  @GetMapping("/personal")
  ReportService.Summary personal() {
    return service.personal();
  }

  @GetMapping("/projects/{id}")
  ReportService.Summary project(@PathVariable UUID id) {
    return service.project(id);
  }
}
