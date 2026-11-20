package com.taskflow.notification;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID userId, Pageable page);

  Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
      UUID userId, Pageable page);

  long countByRecipientIdAndReadAtIsNull(UUID userId);

  boolean existsByDedupKey(String dedupKey);

  @Modifying
  @Query("update Notification n set n.readAt=:now where n.recipient.id=:id and n.readAt is null")
  void readAll(UUID id, Instant now);
}
