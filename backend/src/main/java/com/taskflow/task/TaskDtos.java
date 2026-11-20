package com.taskflow.task;

import com.taskflow.user.UserDtos;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class TaskDtos {
  private TaskDtos() {}

  public record Create(
      @NotNull UUID projectId,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 20000) String description,
      @NotNull Priority priority,
      LocalDate dueDate,
      UUID assigneeId,
      UUID teamId) {}

  public record Update(
      @NotBlank @Size(max = 200) String title,
      @Size(max = 20000) String description,
      @NotNull Priority priority,
      LocalDate dueDate,
      UUID teamId,
      @NotNull Long version) {}

  public record Status(@NotNull TaskStatus status, @NotNull Long version) {}

  public record Assignment(UUID assigneeId, @NotNull Long version) {}

  public record Reassignment(@NotBlank @Size(max = 2000) String reason) {}

  public record Capabilities(
      boolean edit, boolean assign, boolean selfAssign, boolean delete, boolean comment) {}

  public record View(
      UUID id,
      String key,
      String title,
      String description,
      UUID projectId,
      String projectName,
      String projectKey,
      UserDtos.Person creator,
      UserDtos.Person assignee,
      UUID teamId,
      String teamName,
      TaskStatus status,
      Priority priority,
      LocalDate dueDate,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt,
      boolean overdue,
      long version,
      Capabilities permissions) {}
}
