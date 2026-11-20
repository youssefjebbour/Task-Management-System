package com.taskflow.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {
  @Id private UUID id = UUID.randomUUID();

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  @Version private long version;

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }
}
