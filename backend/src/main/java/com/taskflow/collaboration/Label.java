package com.taskflow.collaboration;

import com.taskflow.common.BaseEntity;
import com.taskflow.project.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "labels")
@Getter
@Setter
public class Label extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Project project;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String color = "#2563b8";
}
