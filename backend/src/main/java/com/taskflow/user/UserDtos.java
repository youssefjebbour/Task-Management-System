package com.taskflow.user;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
  private UserDtos() {}

  public record Profile(
      UUID id,
      String email,
      String username,
      String fullName,
      String jobTitle,
      SystemRole systemRole,
      boolean active,
      Instant createdAt) {
    public static Profile of(User u) {
      return new Profile(
          u.getId(),
          u.getEmail(),
          u.getUsername(),
          u.getFullName(),
          u.getJobTitle(),
          u.getSystemRole(),
          u.isActive(),
          u.getCreatedAt());
    }
  }

  public record Person(UUID id, String fullName, String username, String jobTitle, boolean active) {
    public static Person of(User u) {
      return u == null
          ? null
          : new Person(u.getId(), u.getFullName(), u.getUsername(), u.getJobTitle(), u.isActive());
    }
  }

  public record Update(
      @NotBlank @Size(max = 100) String fullName, @Size(max = 120) String jobTitle) {}

  public record Password(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  public record AdminUpdate(@NotNull SystemRole systemRole, @NotNull Boolean active) {}
}
