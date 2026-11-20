package com.taskflow.collaboration;

import com.taskflow.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CollaborationController {
  private final CollaborationService service;

  @GetMapping("/tasks/{id}/comments")
  PageResponse<CollaborationService.CommentView> comments(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
    return service.comments(id, page, size);
  }

  @PostMapping("/tasks/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  CollaborationService.CommentView addComment(
      @PathVariable UUID id, @Valid @RequestBody CollaborationService.CommentInput r) {
    return service.addComment(id, r.body());
  }

  @PutMapping("/comments/{id}")
  CollaborationService.CommentView edit(
      @PathVariable UUID id, @Valid @RequestBody CollaborationService.CommentInput r) {
    return service.editComment(id, r.body());
  }

  @DeleteMapping("/comments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteComment(@PathVariable UUID id) {
    service.deleteComment(id);
  }

  @GetMapping("/projects/{id}/labels")
  List<CollaborationService.LabelView> labels(@PathVariable UUID id) {
    return service.labels(id);
  }

  @PostMapping("/projects/{id}/labels")
  @ResponseStatus(HttpStatus.CREATED)
  CollaborationService.LabelView createLabel(
      @PathVariable UUID id, @Valid @RequestBody CollaborationService.LabelInput r) {
    return service.createLabel(id, r);
  }

  @DeleteMapping("/labels/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteLabel(@PathVariable UUID id) {
    service.deleteLabel(id);
  }

  @GetMapping("/tasks/{id}/labels")
  List<CollaborationService.LabelView> taskLabels(@PathVariable UUID id) {
    return service.taskLabels(id);
  }

  @PostMapping("/tasks/{id}/labels")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void attachLabel(@PathVariable UUID id, @Valid @RequestBody CollaborationService.IdInput r) {
    service.attachLabel(id, r.id());
  }

  @DeleteMapping("/tasks/{id}/labels/{labelId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void detachLabel(@PathVariable UUID id, @PathVariable UUID labelId) {
    service.detachLabel(id, labelId);
  }

  @GetMapping("/tasks/{id}/dependencies")
  CollaborationService.Dependencies dependencies(@PathVariable UUID id) {
    return service.dependencies(id);
  }

  @PostMapping("/tasks/{id}/dependencies")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void addDependency(@PathVariable UUID id, @Valid @RequestBody CollaborationService.IdInput r) {
    service.addDependency(id, r.id());
  }

  @DeleteMapping("/dependencies/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeDependency(@PathVariable UUID id) {
    service.removeDependency(id);
  }

  @GetMapping("/tasks/{id}/recurrence")
  Object recurrence(@PathVariable UUID id) {
    var r = service.recurrence(id);
    return r == null ? Collections.emptyMap() : r;
  }

  @PutMapping("/tasks/{id}/recurrence")
  CollaborationService.RecurrenceView recurrence(
      @PathVariable UUID id, @Valid @RequestBody CollaborationService.RecurrenceInput r) {
    return service.recurrence(id, r);
  }
}
