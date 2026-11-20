package com.taskflow.collaboration;

import com.taskflow.common.BaseEntity;
import com.taskflow.task.Task;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "task_dependencies")
@Getter
@Setter
public class TaskDependency extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Task task;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Task blocker;
}
