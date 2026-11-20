package com.taskflow.team;

import com.taskflow.common.BaseEntity;
import com.taskflow.project.*;
import com.taskflow.user.*;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "team_members")
@Getter
@Setter
public class TeamMember extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Team team;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User user;
}
