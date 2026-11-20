package com.taskflow.auth;

import com.taskflow.user.UserDtos;
import jakarta.validation.constraints.*;

public final class AuthDtos {
  private AuthDtos() {}

  public record Register(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank
          @Pattern(
              regexp = "[a-zA-Z0-9_]{3,40}",
              message = "Use 3–40 letters, numbers or underscores")
          String username,
      @NotBlank @Size(max = 100) String fullName,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record Login(
      @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public record Forgot(@NotBlank @Email @Size(max = 254) String email) {}

  public record Reset(
      @NotBlank @Size(max = 128) String token,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record Session(String accessToken, long expiresIn, UserDtos.Profile user) {}

  public record Message(String message) {}

  public record Issued(Session session, String refreshToken) {}
}
