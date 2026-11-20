package com.taskflow.user;

import com.taskflow.activity.EventService;
import com.taskflow.auth.*;
import com.taskflow.common.*;
import com.taskflow.project.ProjectMemberRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
  private final UserRepository users;
  private final Actor actor;
  private final PasswordEncoder passwords;
  private final RefreshTokenRepository refresh;
  private final ProjectMemberRepository memberships;
  private final EventService events;

  public UserDtos.Profile me() {
    return UserDtos.Profile.of(actor.user());
  }

  @Transactional
  public UserDtos.Profile update(UserDtos.Update r) {
    var u = actor.user();
    u.setFullName(r.fullName().trim());
    u.setJobTitle(r.jobTitle());
    return UserDtos.Profile.of(u);
  }

  @Transactional
  public void password(UserDtos.Password r) {
    var u = actor.user();
    if (!passwords.matches(r.currentPassword(), u.getPasswordHash()))
      throw ApiException.badRequest("Current password is incorrect");
    AuthService.validatePasswordBytes(r.newPassword());
    u.setPasswordHash(passwords.encode(r.newPassword()));
    u.setTokenVersion(u.getTokenVersion() + 1);
    refresh.revokeUser(u.getId(), Instant.now());
    events.record(null, null, "PASSWORD_CHANGED", "Changed account password");
  }

  public List<UserDtos.Person> directory() {
    return (actor.isAdmin()
            ? users.findAll(Sort.by("fullName"))
            : memberships.colleagues(actor.id()))
        .stream().map(UserDtos.Person::of).toList();
  }

  public PageResponse<UserDtos.Profile> list(String q, int page, int size) {
    actor.requireAdmin();
    return PageResponse.of(
        users
            .findAll(
                (root, query, cb) ->
                    cb.or(
                        cb.like(
                            cb.lower(root.get("fullName")), "%" + q.toLowerCase(Locale.ROOT) + "%"),
                        cb.like(
                            cb.lower(root.get("email")), "%" + q.toLowerCase(Locale.ROOT) + "%")),
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(UserDtos.Profile::of));
  }

  @Transactional
  public UserDtos.Profile admin(UUID id, UserDtos.AdminUpdate r) {
    actor.requireAdmin();
    users.lockAdministrators();
    if (!users.existsByIdAndSystemRoleAndActiveTrue(actor.id(), SystemRole.ADMIN))
      throw ApiException.forbidden();
    var u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    if (id.equals(actor.id()) && (!r.active() || r.systemRole() != SystemRole.ADMIN))
      throw ApiException.badRequest("You cannot deactivate or demote your own admin account");
    u.setSystemRole(r.systemRole());
    u.setActive(r.active());
    u.setTokenVersion(u.getTokenVersion() + 1);
    refresh.revokeUser(id, Instant.now());
    events.record(null, null, "USER_CHANGED", "Updated role or activation for " + u.getFullName());
    return UserDtos.Profile.of(u);
  }
}
