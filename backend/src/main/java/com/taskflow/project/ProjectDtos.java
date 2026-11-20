package com.taskflow.project;

import com.taskflow.user.UserDtos;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class ProjectDtos {
  private ProjectDtos() {}

  public record Create(
      @NotBlank @Size(max = 120) String name,
      @NotBlank
          @Pattern(
              regexp = "[A-Za-z][A-Za-z0-9]{1,9}",
              message = "Use 2–10 letters or numbers, starting with a letter")
          String projectKey,
      @Size(max = 10000) String description,
      LocalDate startDate,
      LocalDate deadline) {}

  public record Update(
      @NotBlank @Size(max = 120) String name,
      @Size(max = 10000) String description,
      @NotNull ProjectStatus status,
      LocalDate startDate,
      LocalDate deadline,
      boolean membersCanCreate,
      boolean membersCanSelfAssign,
      boolean membersCanAssign,
      boolean blockIncompleteDependencies,
      @NotNull Long version) {}

  public record View(
      UUID id,
      String name,
      String projectKey,
      String description,
      ProjectStatus status,
      UserDtos.Person owner,
      LocalDate startDate,
      LocalDate deadline,
      boolean membersCanCreate,
      boolean membersCanSelfAssign,
      boolean membersCanAssign,
      boolean blockIncompleteDependencies,
      ProjectRole role,
      boolean canManage,
      boolean canCreateTask,
      boolean canTransfer,
      boolean canReport,
      long totalTasks,
      long completedTasks,
      long version,
      Instant createdAt) {}

  public record Member(UUID id, UserDtos.Person user, ProjectRole role, Instant joinedAt) {}

  public record AddMember(@NotBlank @Email String email, @NotNull ProjectRole role) {}

  public record ChangeRole(@NotNull ProjectRole role) {}

  public record Transfer(@NotNull UUID userId) {}
}
