package com.taskflow.activity;

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
@Table(name = "activity_logs")
@Getter
@Setter
public class ActivityLog extends BaseEntity {
  private UUID projectId;
  private UUID taskId;
  private UUID actorId;

  @Column(nullable = false)
  private String actorName;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false, columnDefinition = "text")
  private String description;
}
