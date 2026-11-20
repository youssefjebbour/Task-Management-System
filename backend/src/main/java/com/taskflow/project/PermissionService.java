package com.taskflow.project;

import com.taskflow.auth.Actor;
import com.taskflow.common.ApiException;
import com.taskflow.task.Task;
import com.taskflow.team.*;
import com.taskflow.user.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The single policy boundary used by services and by response capabilities. */
@Service
@RequiredArgsConstructor
public class PermissionService {
  private final Actor actor;
  private final ProjectMemberRepository members;
  private final TeamMemberRepository teams;

  public ProjectRole role(Project project) {
    var attributes =
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
    var request =
        attributes
                instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
            ? servlet.getRequest()
            : null;
    String key = "taskflow.role." + actor.id() + "." + project.getId();
    if (request != null && request.getMethod().equals("GET")) {
      Object cached = request.getAttribute(key);
      if (cached != null) return cached instanceof ProjectRole r ? r : null;
      var result =
          members
              .findByProjectIdAndUserId(project.getId(), actor.id())
              .map(ProjectMember::getRole)
              .orElse(null);
      request.setAttribute(key, result == null ? Boolean.FALSE : result);
      return result;
    }
    return members
        .findByProjectIdAndUserId(project.getId(), actor.id())
        .map(ProjectMember::getRole)
        .orElse(null);
  }

  public boolean admin() {
    return actor.isAdmin();
  }

  public void read(Project p) {
    if (!admin() && role(p) == null) throw ApiException.forbidden();
  }

  public boolean manages(Project p) {
    var r = role(p);
    return admin() || r == ProjectRole.OWNER || r == ProjectRole.MANAGER;
  }

  public void manage(Project p) {
    read(p);
    if (!manages(p)) throw ApiException.forbidden();
  }

  public void owner(Project p) {
    if (!admin() && !p.getOwner().getId().equals(actor.id())) throw ApiException.forbidden();
  }

  public void writable(Project p) {
    if (p.getStatus() == ProjectStatus.ARCHIVED)
      throw ApiException.badRequest("Archived projects are read-only. Restore the project first.");
  }

  public boolean inTeam(Team team, UUID user) {
    return team != null && teams.existsByTeamIdAndUserId(team.getId(), user);
  }

  public boolean canCreate(Project p) {
    var r = role(p);
    return manages(p)
        || r == ProjectRole.TEAM_LEAD
        || (r == ProjectRole.MEMBER && p.isMembersCanCreate());
  }

  public void create(Project p, Team team) {
    read(p);
    writable(p);
    if (!canCreate(p)) throw ApiException.forbidden();
    if (!manages(p) && role(p) == ProjectRole.TEAM_LEAD && !inTeam(team, actor.id()))
      throw ApiException.forbidden();
  }

  public boolean canEdit(Task t) {
    if (t.getProject().getStatus() == ProjectStatus.ARCHIVED) return false;
    var r = role(t.getProject());
    return manages(t.getProject())
        || (r == ProjectRole.TEAM_LEAD && inTeam(t.getTeam(), actor.id()))
        || (r == ProjectRole.MEMBER
            && t.getAssignee() != null
            && t.getAssignee().getId().equals(actor.id()));
  }

  public void edit(Task t) {
    read(t.getProject());
    writable(t.getProject());
    if (!canEdit(t)) throw ApiException.forbidden();
  }

  public boolean canDelete(Task t) {
    return t.getProject().getStatus() != ProjectStatus.ARCHIVED
        && (manages(t.getProject())
            || (role(t.getProject()) == ProjectRole.TEAM_LEAD && inTeam(t.getTeam(), actor.id())));
  }

  public void delete(Task t) {
    read(t.getProject());
    writable(t.getProject());
    if (!canDelete(t)) throw ApiException.forbidden();
  }

  public boolean canAssign(Task t) {
    var r = role(t.getProject());
    return t.getProject().getStatus() != ProjectStatus.ARCHIVED
        && (manages(t.getProject())
            || (r == ProjectRole.TEAM_LEAD && inTeam(t.getTeam(), actor.id()))
            || (r == ProjectRole.MEMBER && t.getProject().isMembersCanAssign()));
  }

  public boolean canSelfAssign(Task t) {
    return t.getProject().getStatus() != ProjectStatus.ARCHIVED
        && role(t.getProject()) == ProjectRole.MEMBER
        && t.getAssignee() == null
        && t.getProject().isMembersCanSelfAssign()
        && (t.getTeam() == null || inTeam(t.getTeam(), actor.id()));
  }

  public void assign(Task t, User target) {
    read(t.getProject());
    writable(t.getProject());
    if (!canAssign(t) && !(canSelfAssign(t) && target != null && target.getId().equals(actor.id())))
      throw ApiException.forbidden();
    eligible(t.getProject(), target);
    if (!manages(t.getProject())
        && role(t.getProject()) == ProjectRole.TEAM_LEAD
        && target != null
        && !inTeam(t.getTeam(), target.getId())) throw ApiException.forbidden();
    if (t.getTeam() != null && target != null && !inTeam(t.getTeam(), target.getId()))
      throw ApiException.badRequest("Assignee must belong to the task's team");
  }

  public void eligible(Project p, User target) {
    if (target == null) return;
    if (!target.isActive()) throw ApiException.badRequest("Assignee must be active");
    var m =
        members
            .findByProjectIdAndUserId(p.getId(), target.getId())
            .orElseThrow(() -> ApiException.badRequest("Assignee must be a project member"));
    if (m.getRole() == ProjectRole.VIEWER)
      throw ApiException.badRequest("Viewers cannot be assigned tasks");
  }

  public boolean canComment(Task t) {
    return t.getProject().getStatus() != ProjectStatus.ARCHIVED
        && (admin()
            || (role(t.getProject()) != null && role(t.getProject()) != ProjectRole.VIEWER));
  }

  public void comment(Task t) {
    read(t.getProject());
    writable(t.getProject());
    if (!canComment(t)) throw ApiException.forbidden();
  }

  public void reports(Project p) {
    read(p);
    if (!manages(p) && role(p) != ProjectRole.TEAM_LEAD) throw ApiException.forbidden();
  }
}
