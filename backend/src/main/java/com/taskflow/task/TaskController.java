package com.taskflow.task;

import com.taskflow.common.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {
  private final TaskService service;

  @GetMapping
  PageResponse<TaskDtos.View> list(@Valid @ModelAttribute TaskFilter filter) {
    return service.list(filter);
  }

  @GetMapping("/{id}")
  TaskDtos.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TaskDtos.View create(@Valid @RequestBody TaskDtos.Create r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  TaskDtos.View update(@PathVariable UUID id, @Valid @RequestBody TaskDtos.Update r) {
    return service.update(id, r);
  }

  @PatchMapping("/{id}/status")
  TaskDtos.View status(@PathVariable UUID id, @Valid @RequestBody TaskDtos.Status r) {
    return service.status(id, r);
  }

  @PatchMapping("/{id}/assignee")
  TaskDtos.View assign(@PathVariable UUID id, @Valid @RequestBody TaskDtos.Assignment r) {
    return service.assign(id, r);
  }

  @PostMapping("/{id}/reassignment-request")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void request(@PathVariable UUID id, @Valid @RequestBody TaskDtos.Reassignment r) {
    service.requestReassignment(id, r.reason());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
