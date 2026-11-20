package com.taskflow.team;

import com.taskflow.common.BaseEntity;
import com.taskflow.project.*;
import com.taskflow.user.*;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "teams")
@Getter
@Setter
public class Team extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Project project;

  @Column(nullable = false)
  private String name;
}
