package com.taskflow.attachment;

import com.taskflow.common.BaseEntity;
import com.taskflow.task.Task;
import com.taskflow.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "attachments")
@Getter
@Setter
public class Attachment extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Task task;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User uploader;

  @Column(nullable = false, length = 180)
  private String filename;

  @Column(nullable = false, unique = true, length = 36)
  private String storageKey;

  @Column(nullable = false, length = 100)
  private String contentType;

  @Column(nullable = false)
  private long sizeBytes;
}
