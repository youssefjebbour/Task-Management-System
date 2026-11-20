package com.taskflow.team;

import com.taskflow.activity.EventService;
import com.taskflow.auth.Actor;
import com.taskflow.common.ApiException;
import com.taskflow.project.*;
import com.taskflow.task.TaskRepository;
import com.taskflow.user.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {
  private final TeamRepository teams;
  private final TeamMemberRepository members;
  private final ProjectService projects;
  private final PermissionService permissions;
  private final UserRepository users;
  private final ProjectMemberRepository projectMembers;
  private final TaskRepository tasks;
  private final Actor actor;
  private final EventService events;

  public record View(UUID id, UUID projectId, String name, List<UserDtos.Person> members) {}

  public record Create(@NotNull UUID projectId, @NotBlank @Size(max = 100) String name) {}

  public record AddMember(@NotNull UUID userId) {}

  public record Rename(@NotBlank @Size(max = 100) String name) {}

  public List<View> list(UUID projectId) {
    projects.require(projectId);
    return teams.findByProjectIdOrderByName(projectId).stream().map(this::view).toList();
  }

  @Transactional
  public View create(Create r) {
    var p = projects.require(r.projectId());
    permissions.manage(p);
    permissions.writable(p);
    var t = new Team();
    t.setProject(p);
    t.setName(r.name().trim());
    teams.save(t);
    events.record(p.getId(), null, "TEAM_CREATED", "Created team " + t.getName());
    return view(t);
  }

  @Transactional
  public View rename(UUID id, String name) {
    var t = require(id);
    permissions.manage(t.getProject());
    permissions.writable(t.getProject());
    t.setName(name.trim());
    events.record(t.getProject().getId(), null, "TEAM_UPDATED", "Renamed team to " + name);
    return view(t);
  }

  @Transactional
  public View add(UUID id, UUID userId) {
    var t = require(id);
    permissions.manage(t.getProject());
    permissions.writable(t.getProject());
    var u =
        users
            .findById(userId)
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.badRequest("Choose an active project member"));
    if (!projectMembers.existsByProjectIdAndUserId(t.getProject().getId(), userId))
      throw ApiException.badRequest("Team members must belong to the project");
    if (members.existsByTeamIdAndUserId(id, userId))
      throw ApiException.conflict("This user already belongs to the team");
    var m = new TeamMember();
    m.setTeam(t);
    m.setUser(u);
    members.saveAndFlush(m);
    events.record(
        t.getProject().getId(),
        null,
        "TEAM_MEMBER_ADDED",
        "Added " + u.getFullName() + " to " + t.getName());
    return view(t);
  }

  @Transactional
  public void remove(UUID id, UUID userId) {
    var t = require(id);
    permissions.manage(t.getProject());
    permissions.writable(t.getProject());
    if (tasks.count(
            (r, q, c) ->
                c.and(
                    c.equal(r.get("team").get("id"), id),
                    c.equal(r.get("assignee").get("id"), userId)))
        > 0)
      throw ApiException.badRequest("Reassign this person's team tasks before removing them");
    var m =
        members
            .findByTeamIdAndUserId(id, userId)
            .orElseThrow(() -> ApiException.notFound("Team member"));
    members.delete(m);
    events.record(
        t.getProject().getId(),
        null,
        "TEAM_MEMBER_REMOVED",
        "Removed " + m.getUser().getFullName() + " from " + t.getName());
  }

  @Transactional
  public void delete(UUID id) {
    var t = require(id);
    permissions.manage(t.getProject());
    permissions.writable(t.getProject());
    if (tasks.count((r, q, c) -> c.equal(r.get("team").get("id"), id)) > 0)
      throw ApiException.badRequest("Move this team's tasks before deleting it");
    events.record(t.getProject().getId(), null, "TEAM_DELETED", "Deleted team " + t.getName());
    teams.delete(t);
  }

  private Team require(UUID id) {
    var t = teams.findById(id).orElseThrow(() -> ApiException.notFound("Team"));
    permissions.read(t.getProject());
    return t;
  }

  private View view(Team t) {
    return new View(
        t.getId(),
        t.getProject().getId(),
        t.getName(),
        members.findByTeamId(t.getId()).stream()
            .map(m -> UserDtos.Person.of(m.getUser()))
            .toList());
  }
}
