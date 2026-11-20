package com.taskflow.project;

import com.taskflow.activity.EventService;
import com.taskflow.auth.Actor;
import com.taskflow.common.*;
import com.taskflow.task.*;
import com.taskflow.team.TeamMemberRepository;
import com.taskflow.user.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final UserRepository users;
  private final TaskRepository tasks;
  private final PermissionService permissions;
  private final Actor actor;
  private final EventService events;
  private final TeamMemberRepository teamMembers;
  private final com.taskflow.attachment.AttachmentRepository attachments;
  private final com.taskflow.attachment.AttachmentStorage attachmentStorage;

  public Project require(UUID id) {
    var p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
    permissions.read(p);
    return p;
  }

  public PageResponse<ProjectDtos.View> list(String q, ProjectStatus status, int page, int size) {
    return PageResponse.of(
        projects
            .accessible(
                actor.id(),
                actor.isAdmin(),
                q,
                status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(this::view));
  }

  public ProjectDtos.View get(UUID id) {
    return view(require(id));
  }

  @Transactional
  public ProjectDtos.View create(ProjectDtos.Create r) {
    dates(r.startDate(), r.deadline());
    String key = r.projectKey().toUpperCase(Locale.ROOT);
    if (projects.existsByProjectKey(key))
      throw ApiException.conflict("Project key is already in use");
    var p = new Project();
    p.setName(r.name().trim());
    p.setProjectKey(key);
    p.setDescription(r.description());
    p.setOwner(actor.user());
    p.setStartDate(r.startDate());
    p.setDeadline(r.deadline());
    projects.save(p);
    var m = new ProjectMember();
    m.setProject(p);
    m.setUser(actor.user());
    m.setRole(ProjectRole.OWNER);
    members.saveAndFlush(m);
    events.record(p.getId(), null, "PROJECT_CREATED", "Created " + p.getName());
    return view(p);
  }

  @Transactional
  public ProjectDtos.View update(UUID id, ProjectDtos.Update r) {
    var p = require(id);
    permissions.manage(p);
    if (p.getVersion() != r.version())
      throw ApiException.conflict("Project changed. Refresh and try again.");
    dates(r.startDate(), r.deadline());
    p.setName(r.name().trim());
    p.setDescription(r.description());
    p.setStatus(r.status());
    p.setStartDate(r.startDate());
    p.setDeadline(r.deadline());
    p.setMembersCanCreate(r.membersCanCreate());
    p.setMembersCanSelfAssign(r.membersCanSelfAssign());
    p.setMembersCanAssign(r.membersCanAssign());
    p.setBlockIncompleteDependencies(r.blockIncompleteDependencies());
    projects.saveAndFlush(p);
    events.record(id, null, "PROJECT_UPDATED", "Updated settings for " + p.getName());
    return view(p);
  }

  @Transactional
  public void delete(UUID id) {
    var p = projects.lockById(id).orElseThrow(() -> ApiException.notFound("Project"));
    permissions.owner(p);
    if (p.getStatus() != ProjectStatus.ARCHIVED)
      throw ApiException.badRequest("Archive the project before deleting it");
    attachmentStorage.deleteAfterCommit(attachments.keysForProject(id));
    events.record(id, null, "PROJECT_DELETED", "Deleted archived project " + p.getName());
    projects.deleteProjectGraph(id);
  }

  public List<ProjectDtos.Member> members(UUID id) {
    require(id);
    return members.findByProjectIdOrderByCreatedAtAsc(id).stream().map(this::member).toList();
  }

  @Transactional
  public ProjectDtos.Member addMember(UUID id, ProjectDtos.AddMember r) {
    var p = require(id);
    permissions.manage(p);
    permissions.writable(p);
    checkRoleGrant(p, r.role());
    var u =
        users
            .findByEmail(r.email().trim().toLowerCase(Locale.ROOT))
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.badRequest("No active account found for that email"));
    if (members.existsByProjectIdAndUserId(id, u.getId()))
      throw ApiException.conflict("This user is already a member");
    var m = new ProjectMember();
    m.setProject(p);
    m.setUser(u);
    m.setRole(r.role());
    members.save(m);
    events.record(id, null, "MEMBER_ADDED", "Added " + u.getFullName() + " as " + r.role());
    events.notify(u, id, null, "Project invitation", "You were added to " + p.getName());
    return member(m);
  }

  @Transactional
  public ProjectDtos.Member role(UUID id, UUID userId, ProjectRole role) {
    var p = require(id);
    permissions.manage(p);
    permissions.writable(p);
    var m = membership(id, userId);
    checkMemberChange(p, m);
    checkRoleGrant(p, role);
    if (role == ProjectRole.VIEWER && tasks.countByProjectIdAndAssigneeId(id, userId) > 0)
      throw ApiException.badRequest("Reassign this member's tasks before making them a viewer");
    m.setRole(role);
    events.record(id, null, "ROLE_CHANGED", "Changed " + m.getUser().getFullName() + " to " + role);
    return member(m);
  }

  @Transactional
  public void removeMember(UUID id, UUID userId) {
    var p = require(id);
    permissions.manage(p);
    permissions.writable(p);
    var m = membership(id, userId);
    checkMemberChange(p, m);
    tasks.unassignMember(id, userId);
    teamMembers.removeFromProject(userId, id);
    members.delete(m);
    events.record(
        id,
        null,
        "MEMBER_REMOVED",
        "Removed " + m.getUser().getFullName() + "; their tasks are now unassigned");
  }

  @Transactional
  public ProjectDtos.View transfer(UUID id, UUID userId) {
    var p = projects.lockById(id).orElseThrow(() -> ApiException.notFound("Project"));
    permissions.owner(p);
    permissions.writable(p);
    if (userId.equals(p.getOwner().getId()))
      throw ApiException.badRequest("This user already owns the project");
    var target = membership(id, userId);
    if (!target.getUser().isActive()) throw ApiException.badRequest("New owner must be active");
    var previous = membership(id, p.getOwner().getId());
    previous.setRole(ProjectRole.MANAGER);
    members.saveAndFlush(previous);
    target.setRole(ProjectRole.OWNER);
    p.setOwner(target.getUser());
    projects.saveAndFlush(p);
    events.record(
        id,
        null,
        "OWNERSHIP_TRANSFERRED",
        "Transferred ownership to " + target.getUser().getFullName());
    return view(p);
  }

  private void checkRoleGrant(Project p, ProjectRole role) {
    if (role == ProjectRole.OWNER)
      throw ApiException.badRequest("Use ownership transfer to appoint an owner");
    if (!actor.isAdmin()
        && permissions.role(p) == ProjectRole.MANAGER
        && role == ProjectRole.MANAGER) throw ApiException.forbidden();
  }

  private void checkMemberChange(Project p, ProjectMember m) {
    if (m.getRole() == ProjectRole.OWNER)
      throw ApiException.badRequest("Transfer ownership before changing or removing the owner");
    if (!actor.isAdmin()
        && permissions.role(p) == ProjectRole.MANAGER
        && m.getRole() == ProjectRole.MANAGER) throw ApiException.forbidden();
  }

  private ProjectMember membership(UUID id, UUID userId) {
    return members
        .findByProjectIdAndUserId(id, userId)
        .orElseThrow(() -> ApiException.notFound("Project member"));
  }

  private ProjectDtos.Member member(ProjectMember m) {
    return new ProjectDtos.Member(
        m.getId(), UserDtos.Person.of(m.getUser()), m.getRole(), m.getCreatedAt());
  }

  public ProjectDtos.View view(Project p) {
    var role = permissions.role(p);
    boolean manage = permissions.manages(p), writable = p.getStatus() != ProjectStatus.ARCHIVED;
    return new ProjectDtos.View(
        p.getId(),
        p.getName(),
        p.getProjectKey(),
        p.getDescription(),
        p.getStatus(),
        UserDtos.Person.of(p.getOwner()),
        p.getStartDate(),
        p.getDeadline(),
        p.isMembersCanCreate(),
        p.isMembersCanSelfAssign(),
        p.isMembersCanAssign(),
        p.isBlockIncompleteDependencies(),
        role,
        manage,
        writable && permissions.canCreate(p),
        actor.isAdmin() || role == ProjectRole.OWNER,
        manage || role == ProjectRole.TEAM_LEAD,
        tasks.countByProjectId(p.getId()),
        tasks.countByProjectIdAndStatus(p.getId(), TaskStatus.COMPLETED),
        p.getVersion(),
        p.getCreatedAt());
  }

  private void dates(LocalDate start, LocalDate deadline) {
    if (start != null && deadline != null && deadline.isBefore(start))
      throw ApiException.badRequest("Deadline cannot precede the start date");
  }
}
