package com.taskflow.notification;

import com.taskflow.common.BaseEntity;
import com.taskflow.project.*;
import com.taskflow.team.*;
import com.taskflow.user.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User recipient;

  private UUID projectId;
  private UUID taskId;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String message;

  private Instant readAt;

  @Column(unique = true)
  private String dedupKey;
}
