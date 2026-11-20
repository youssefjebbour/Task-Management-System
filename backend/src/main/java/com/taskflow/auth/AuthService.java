package com.taskflow.auth;

import com.taskflow.common.ApiException;
import com.taskflow.user.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final RefreshTokenRepository refresh;
  private final PasswordResetRepository resets;
  private final PasswordEncoder passwords;
  private final TokenService tokens;
  private final ApplicationEventPublisher events;
  private final long days;
  private final String appUrl, from, dummyHash;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refresh,
      PasswordResetRepository resets,
      PasswordEncoder passwords,
      TokenService tokens,
      ApplicationEventPublisher events,
      @Value("${app.refresh-token-days}") long days,
      @Value("${app.url}") String appUrl,
      @Value("${app.mail-from}") String from) {
    this.users = users;
    this.refresh = refresh;
    this.resets = resets;
    this.passwords = passwords;
    this.tokens = tokens;
    this.events = events;
    this.days = days;
    this.appUrl = appUrl;
    this.from = from;
    this.dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public AuthDtos.Issued register(AuthDtos.Register r) {
    String email = r.email().trim().toLowerCase(Locale.ROOT),
        username = r.username().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email) || users.existsByUsername(username))
      throw ApiException.conflict("Email or username is already registered");
    validatePasswordBytes(r.password());
    var u = new User();
    u.setEmail(email);
    u.setUsername(username);
    u.setFullName(r.fullName().trim());
    u.setPasswordHash(passwords.encode(r.password()));
    users.saveAndFlush(u);
    return issue(u, UUID.randomUUID());
  }

  @Transactional
  public AuthDtos.Issued login(AuthDtos.Login r) {
    var u = users.findByEmail(r.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
    boolean valid = passwords.matches(r.password(), u == null ? dummyHash : u.getPasswordHash());
    if (!valid || u == null || !u.isActive())
      throw ApiException.unauthorized("Email or password is incorrect");
    return issue(u, UUID.randomUUID());
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthDtos.Issued refresh(String raw) {
    if (raw == null) throw ApiException.unauthorized("No refresh session");
    var old =
        refresh
            .lockByHash(TokenService.hash(raw))
            .orElseThrow(() -> ApiException.unauthorized("Invalid refresh session"));
    if (old.getConsumedAt() != null) {
      refresh.revokeFamily(old.getFamilyId(), Instant.now());
      throw ApiException.unauthorized("Session reuse detected. Sign in again.");
    }
    if (old.getRevokedAt() != null
        || old.getExpiresAt().isBefore(Instant.now())
        || !old.getUser().isActive()) throw ApiException.unauthorized("Refresh session expired");
    old.setConsumedAt(Instant.now());
    refresh.saveAndFlush(old);
    return issue(old.getUser(), old.getFamilyId());
  }

  @Transactional
  public void logout(String raw) {
    if (raw != null)
      refresh
          .lockByHash(TokenService.hash(raw))
          .ifPresent(t -> refresh.revokeFamily(t.getFamilyId(), Instant.now()));
  }

  @Transactional
  public void forgot(AuthDtos.Forgot r) {
    users
        .findByEmail(r.email().trim().toLowerCase(Locale.ROOT))
        .filter(User::isActive)
        .ifPresent(
            u -> {
              resets.invalidateUser(u.getId(), Instant.now());
              String raw = tokens.opaque();
              var token = new PasswordResetToken();
              token.setUser(u);
              token.setTokenHash(TokenService.hash(raw));
              token.setExpiresAt(Instant.now().plusSeconds(900));
              resets.save(token);
              var message = new SimpleMailMessage();
              message.setFrom(from);
              message.setTo(u.getEmail());
              message.setSubject("Reset your TaskFlow password");
              message.setText(
                  "A password reset was requested for your TaskFlow account. This link expires in 15 minutes and works once:\n\n"
                      + appUrl
                      + "/reset-password?token="
                      + raw
                      + "\n\nIf you did not request this, ignore this email.");
              events.publishEvent(new ResetMailDelivery.Requested(message));
            });
  }

  @Transactional
  public void reset(AuthDtos.Reset r) {
    var token =
        resets
            .lockByHash(TokenService.hash(r.token()))
            .orElseThrow(() -> ApiException.badRequest("Reset link is invalid or expired"));
    if (token.getUsedAt() != null
        || token.getExpiresAt().isBefore(Instant.now())
        || !token.getUser().isActive())
      throw ApiException.badRequest("Reset link is invalid or expired");
    validatePasswordBytes(r.password());
    var u = token.getUser();
    u.setPasswordHash(passwords.encode(r.password()));
    u.setTokenVersion(u.getTokenVersion() + 1);
    token.setUsedAt(Instant.now());
    refresh.revokeUser(u.getId(), Instant.now());
    resets.invalidateUser(u.getId(), Instant.now());
  }

  private AuthDtos.Issued issue(User u, UUID family) {
    String raw = tokens.opaque();
    var t = new RefreshToken();
    t.setUser(u);
    t.setTokenHash(TokenService.hash(raw));
    t.setFamilyId(family);
    t.setExpiresAt(Instant.now().plus(Duration.ofDays(days)));
    refresh.save(t);
    return new AuthDtos.Issued(
        new AuthDtos.Session(tokens.access(u), tokens.expiresIn(), UserDtos.Profile.of(u)), raw);
  }

  public static void validatePasswordBytes(String password) {
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.badRequest("Password must be at most 72 UTF-8 bytes");
  }
}
