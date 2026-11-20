package com.taskflow.task;

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
@Table(name = "tasks")
@Getter
@Setter
public class Task extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User creator;

  @ManyToOne(fetch = FetchType.LAZY)
  private User assignee;

  @ManyToOne(fetch = FetchType.LAZY)
  private Team team;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TaskStatus status = TaskStatus.TODO;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Priority priority = Priority.MEDIUM;

  private LocalDate dueDate;
  private Instant completedAt;
  private UUID recurrenceId;
  private LocalDate occurrenceDate;
}
