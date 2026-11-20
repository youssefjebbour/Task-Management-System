package com.taskflow.project;

import com.taskflow.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Validated
public class ProjectController {
  private final ProjectService service;

  @GetMapping
  PageResponse<ProjectDtos.View> list(
      @RequestParam(defaultValue = "") @Size(max = 200) String q,
      @RequestParam(required = false) ProjectStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(q, status, page, size);
  }

  @GetMapping("/{id}")
  ProjectDtos.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ProjectDtos.View create(@Valid @RequestBody ProjectDtos.Create r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  ProjectDtos.View update(@PathVariable UUID id, @Valid @RequestBody ProjectDtos.Update r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  @GetMapping("/{id}/members")
  List<ProjectDtos.Member> members(@PathVariable UUID id) {
    return service.members(id);
  }

  @PostMapping("/{id}/members")
  @ResponseStatus(HttpStatus.CREATED)
  ProjectDtos.Member add(@PathVariable UUID id, @Valid @RequestBody ProjectDtos.AddMember r) {
    return service.addMember(id, r);
  }

  @PatchMapping("/{id}/members/{userId}")
  ProjectDtos.Member role(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      @Valid @RequestBody ProjectDtos.ChangeRole r) {
    return service.role(id, userId, r.role());
  }

  @DeleteMapping("/{id}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@PathVariable UUID id, @PathVariable UUID userId) {
    service.removeMember(id, userId);
  }

  @PostMapping("/{id}/transfer")
  ProjectDtos.View transfer(@PathVariable UUID id, @Valid @RequestBody ProjectDtos.Transfer r) {
    return service.transfer(id, r.userId());
  }
}
