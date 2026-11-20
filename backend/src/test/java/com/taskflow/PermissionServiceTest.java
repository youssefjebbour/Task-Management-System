package com.taskflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.taskflow.auth.Actor;
import com.taskflow.common.ApiException;
import com.taskflow.project.*;
import com.taskflow.task.Task;
import com.taskflow.team.*;
import com.taskflow.user.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {
  @Mock Actor actor;
  @Mock ProjectMemberRepository members;
  @Mock TeamMemberRepository teams;
  @InjectMocks PermissionService permissions;

  @Test
  void teamLeadCannotAssignOutsideTheirOwnTeam() {
    var lead = new User();
    var target = new User();
    var p = new Project();
    p.setOwner(new User());
    var team = new Team();
    team.setProject(p);
    var task = new Task();
    task.setProject(p);
    task.setTeam(team);
    var leadMembership = new ProjectMember();
    leadMembership.setRole(ProjectRole.TEAM_LEAD);
    var targetMembership = new ProjectMember();
    targetMembership.setRole(ProjectRole.MEMBER);
    when(actor.id()).thenReturn(lead.getId());
    when(members.findByProjectIdAndUserId(p.getId(), lead.getId()))
        .thenReturn(Optional.of(leadMembership));
    when(members.findByProjectIdAndUserId(p.getId(), target.getId()))
        .thenReturn(Optional.of(targetMembership));
    when(teams.existsByTeamIdAndUserId(team.getId(), lead.getId())).thenReturn(true);
    when(teams.existsByTeamIdAndUserId(team.getId(), target.getId())).thenReturn(false);
    assertThatThrownBy(() -> permissions.assign(task, target))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);
    when(teams.existsByTeamIdAndUserId(team.getId(), target.getId())).thenReturn(true);
    assertThatCode(() -> permissions.assign(task, target)).doesNotThrowAnyException();
  }

  @Test
  void archivedProjectsRejectWritesEvenForAdmins() {
    var p = new Project();
    p.setStatus(ProjectStatus.ARCHIVED);
    assertThatThrownBy(() -> permissions.writable(p)).isInstanceOf(ApiException.class);
  }
}
