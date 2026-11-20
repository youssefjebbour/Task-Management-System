package com.taskflow.project;

import com.taskflow.common.BaseEntity;
import com.taskflow.team.*;
import com.taskflow.user.*;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project extends BaseEntity {
  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String projectKey;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProjectStatus status = ProjectStatus.PLANNING;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User owner;

  private LocalDate startDate;
  private LocalDate deadline;

  @Column(nullable = false)
  private boolean membersCanCreate = true;

  @Column(nullable = false)
  private boolean membersCanSelfAssign = true;

  @Column(nullable = false)
  private boolean membersCanAssign = false;

  @Column(nullable = false)
  private boolean blockIncompleteDependencies = true;
}
