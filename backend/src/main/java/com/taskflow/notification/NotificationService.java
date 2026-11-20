package com.taskflow.notification;

import com.taskflow.auth.Actor;
import com.taskflow.common.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
  private final NotificationRepository notifications;
  private final Actor actor;

  public record View(
      UUID id,
      UUID projectId,
      UUID taskId,
      String title,
      String message,
      Instant readAt,
      Instant createdAt) {
    static View of(Notification n) {
      return new View(
          n.getId(),
          n.getProjectId(),
          n.getTaskId(),
          n.getTitle(),
          n.getMessage(),
          n.getReadAt(),
          n.getCreatedAt());
    }
  }

  public PageResponse<View> list(boolean unread, int page, int size) {
    var p = PageRequest.of(page, size);
    return PageResponse.of(
        (unread
                ? notifications.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(actor.id(), p)
                : notifications.findByRecipientIdOrderByCreatedAtDesc(actor.id(), p))
            .map(View::of));
  }

  public long count() {
    return notifications.countByRecipientIdAndReadAtIsNull(actor.id());
  }

  @Transactional
  public View read(UUID id, boolean read) {
    var n = notifications.findById(id).orElseThrow(() -> ApiException.notFound("Notification"));
    if (!n.getRecipient().getId().equals(actor.id())) throw ApiException.forbidden();
    n.setReadAt(read ? Instant.now() : null);
    return View.of(n);
  }

  @Transactional
  public void readAll() {
    notifications.readAll(actor.id(), Instant.now());
  }
}
