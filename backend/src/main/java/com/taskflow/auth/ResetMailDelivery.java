package com.taskflow.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Best-effort asynchronous delivery. Production deployments should use a durable outbox. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResetMailDelivery {
  private final JavaMailSender mail;

  public record Requested(SimpleMailMessage message) {}

  @Async("mailExecutor")
  @TransactionalEventListener
  public void deliver(Requested request) {
    try {
      mail.send(request.message());
    } catch (Exception e) {
      log.warn("Password reset mail delivery failed ({})", e.getClass().getSimpleName());
    }
  }
}
