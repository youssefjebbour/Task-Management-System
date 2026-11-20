package com.taskflow.collaboration;

import com.taskflow.common.BaseEntity;
import com.taskflow.task.Task;
import com.taskflow.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "comments")
@Getter
@Setter
public class Comment extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Task task;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User author;

  @Column(nullable = false, columnDefinition = "text")
  private String body;
}
