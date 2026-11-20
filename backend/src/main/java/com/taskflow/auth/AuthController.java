package com.taskflow.auth;

import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;
  private final boolean secure;
  private final long days;

  public AuthController(
      AuthService service,
      @Value("${app.cookie-secure}") boolean secure,
      @Value("${app.refresh-token-days}") long days) {
    this.service = service;
    this.secure = secure;
    this.days = days;
  }

  @PostMapping("/register")
  ResponseEntity<AuthDtos.Session> register(@Valid @RequestBody AuthDtos.Register request) {
    return session(service.register(request), HttpStatus.CREATED);
  }

  @PostMapping("/login")
  ResponseEntity<AuthDtos.Session> login(@Valid @RequestBody AuthDtos.Login request) {
    return session(service.login(request), HttpStatus.OK);
  }

  @PostMapping("/refresh")
  ResponseEntity<AuthDtos.Session> refresh(
      @CookieValue(value = "tf_refresh", required = false) String token) {
    return session(service.refresh(token), HttpStatus.OK);
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(@CookieValue(value = "tf_refresh", required = false) String token) {
    service.logout(token);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
        .build();
  }

  @PostMapping("/forgot-password")
  AuthDtos.Message forgot(@Valid @RequestBody AuthDtos.Forgot request) {
    service.forgot(request);
    return new AuthDtos.Message(
        "If an active account matches that email, a reset link has been sent.");
  }

  @PostMapping("/reset-password")
  AuthDtos.Message reset(@Valid @RequestBody AuthDtos.Reset request) {
    service.reset(request);
    return new AuthDtos.Message("Password reset. Sign in with your new password.");
  }

  private ResponseEntity<AuthDtos.Session> session(AuthDtos.Issued issued, HttpStatus status) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, cookie(issued.refreshToken(), Duration.ofDays(days)))
        .body(issued.session());
  }

  private String cookie(String token, Duration maxAge) {
    return ResponseCookie.from("tf_refresh", token)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(maxAge)
        .build()
        .toString();
  }
}
