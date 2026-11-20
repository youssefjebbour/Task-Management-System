package com.taskflow.auth;

import com.taskflow.common.ApiException;
import com.taskflow.user.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Actor {
  private final UserRepository users;

  public User user() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
      throw ApiException.unauthorized("Sign in to continue");
    return users
        .findById(UUID.fromString(auth.getName()))
        .filter(User::isActive)
        .orElseThrow(() -> ApiException.unauthorized("Account is inactive"));
  }

  public UUID id() {
    return user().getId();
  }

  public boolean isAdmin() {
    return user().getSystemRole() == SystemRole.ADMIN;
  }

  public void requireAdmin() {
    if (!isAdmin()) throw ApiException.forbidden();
  }
}
