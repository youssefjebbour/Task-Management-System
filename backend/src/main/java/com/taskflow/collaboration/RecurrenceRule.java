package com.taskflow.collaboration;

import com.taskflow.common.BaseEntity;
import com.taskflow.task.Task;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recurrence_rules")
@Getter
@Setter
public class RecurrenceRule extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Task template;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Frequency frequency;

  @Column(nullable = false)
  private int anchorDay;

  @Column(nullable = false)
  private LocalDate nextDate;

  @Column(nullable = false)
  private boolean active = true;
}
