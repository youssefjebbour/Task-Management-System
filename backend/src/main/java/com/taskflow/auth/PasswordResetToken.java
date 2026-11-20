package com.taskflow.auth;

import com.taskflow.common.BaseEntity;
import com.taskflow.project.*;
import com.taskflow.team.*;
import com.taskflow.user.*;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
public class PasswordResetToken extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User user;

  @Column(nullable = false, unique = true)
  private String tokenHash;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant usedAt;
}
