package com.taskflow.user;

import com.taskflow.common.BaseEntity;
import com.taskflow.project.*;
import com.taskflow.team.*;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_users")
@Getter
@Setter
public class User extends BaseEntity {
  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SystemRole systemRole = SystemRole.USER;

  @Column(nullable = false)
  private boolean active = true;

  private String jobTitle;

  @Column(nullable = false)
  private int tokenVersion = 0;
}
