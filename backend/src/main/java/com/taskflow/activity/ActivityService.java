package com.taskflow.activity;

import com.taskflow.auth.Actor;
import com.taskflow.common.PageResponse;
import com.taskflow.project.ProjectService;
import com.taskflow.task.TaskService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {
  private final ActivityRepository logs;
  private final Actor actor;
  private final ProjectService projects;
  private final TaskService tasks;

  public record View(
      UUID id,
      UUID projectId,
      UUID taskId,
      String actorName,
      String action,
      String description,
      Instant createdAt) {
    static View of(ActivityLog a) {
      return new View(
          a.getId(),
          a.getProjectId(),
          a.getTaskId(),
          a.getActorName(),
          a.getAction(),
          a.getDescription(),
          a.getCreatedAt());
    }
  }

  public PageResponse<View> list(UUID projectId, UUID taskId, boolean audit, int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    if (audit) {
      actor.requireAdmin();
      return PageResponse.of(logs.findAll(pageable).map(View::of));
    }
    if (taskId != null) {
      tasks.require(taskId);
      return PageResponse.of(logs.findByTaskIdOrderByCreatedAtDesc(taskId, pageable).map(View::of));
    }
    if (projectId != null) {
      projects.require(projectId);
      return PageResponse.of(
          logs.findByProjectIdOrderByCreatedAtDesc(projectId, pageable).map(View::of));
    }
    return PageResponse.of(logs.feed(actor.id(), pageable).map(View::of));
  }
}
