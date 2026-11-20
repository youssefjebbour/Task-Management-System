package com.taskflow.task;

import com.taskflow.activity.EventService;
import com.taskflow.auth.Actor;
import com.taskflow.common.*;
import com.taskflow.project.*;
import com.taskflow.team.*;
import com.taskflow.user.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {
  private final TaskRepository tasks;
  private final ProjectService projects;
  private final PermissionService permissions;
  private final UserRepository users;
  private final TeamRepository teams;
  private final Actor actor;
  private final Clock clock;
  private final EventService events;
  private final com.taskflow.collaboration.DependencyRepository dependencies;
  private final ProjectRepository projectRepo;
  private final com.taskflow.attachment.AttachmentRepository attachments;
  private final com.taskflow.attachment.AttachmentStorage attachmentStorage;

  public Task require(UUID id) {
    var t = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
    permissions.read(t.getProject());
    return t;
  }

  public TaskDtos.View get(UUID id) {
    return view(require(id));
  }

  public PageResponse<TaskDtos.View> list(TaskFilter filter) {
    if (filter.getProjectId() != null) projects.require(filter.getProjectId());
    if (filter.getDueFrom() != null
        && filter.getDueTo() != null
        && filter.getDueTo().isBefore(filter.getDueFrom()))
      throw ApiException.badRequest("Due-date range is invalid");
    return PageResponse.of(
        tasks
            .findAll(
                TaskSpecifications.filtered(filter, actor.id(), actor.isAdmin(), clock),
                PageRequest.of(filter.getPage(), filter.getSize()))
            .map(this::view));
  }

  @Transactional
  public TaskDtos.View create(TaskDtos.Create r) {
    var p = projects.require(r.projectId());
    var team = team(r.teamId(), p);
    permissions.create(p, team);
    var t = new Task();
    t.setProject(p);
    t.setCreator(actor.user());
    t.setTitle(r.title().trim());
    t.setDescription(r.description());
    t.setPriority(r.priority());
    t.setDueDate(r.dueDate());
    t.setTeam(team);
    if (r.assigneeId() != null) {
      var u = user(r.assigneeId());
      permissions.assign(t, u);
      t.setAssignee(u);
    }
    tasks.saveAndFlush(t);
    events.record(p.getId(), t.getId(), "TASK_CREATED", "Created " + t.getTitle());
    events.notify(t.getAssignee(), p.getId(), t.getId(), "Task assigned", t.getTitle());
    return view(t);
  }

  @Transactional
  public TaskDtos.View update(UUID id, TaskDtos.Update r) {
    var t = require(id);
    permissions.edit(t);
    version(t, r.version());
    UUID previous = t.getTeam() == null ? null : t.getTeam().getId();
    if (!Objects.equals(previous, r.teamId())) {
      if (!permissions.manages(t.getProject())) throw ApiException.forbidden();
      var team = team(r.teamId(), t.getProject());
      if (team != null
          && t.getAssignee() != null
          && !permissions.inTeam(team, t.getAssignee().getId()))
        throw ApiException.badRequest("Reassign the task before changing its team");
      t.setTeam(team);
    }
    t.setTitle(r.title().trim());
    t.setDescription(r.description());
    t.setPriority(r.priority());
    t.setDueDate(r.dueDate());
    tasks.saveAndFlush(t);
    events.record(t.getProject().getId(), id, "TASK_UPDATED", "Updated " + t.getTitle());
    events.notify(t.getAssignee(), t.getProject().getId(), id, "Task updated", t.getTitle());
    return view(t);
  }

  @Transactional
  public TaskDtos.View status(UUID id, TaskDtos.Status r) {
    var t = require(id);
    permissions.edit(t);
    version(t, r.version());
    if (t.getStatus() == r.status()) return view(t);
    projectRepo.lockById(t.getProject().getId()).orElseThrow();
    if (r.status() == TaskStatus.COMPLETED
        && t.getProject().isBlockIncompleteDependencies()
        && dependencies.incompleteCount(id) > 0)
      throw ApiException.badRequest("Complete the blocking tasks first");
    var old = t.getStatus();
    t.setStatus(r.status());
    t.setCompletedAt(r.status() == TaskStatus.COMPLETED ? Instant.now() : null);
    tasks.saveAndFlush(t);
    events.record(
        t.getProject().getId(),
        id,
        "STATUS_CHANGED",
        t.getTitle() + ": " + old + " → " + r.status());
    events.notify(
        t.getAssignee(),
        t.getProject().getId(),
        id,
        r.status() == TaskStatus.COMPLETED ? "Task completed" : "Status changed",
        t.getTitle());
    if (t.getCreator() != t.getAssignee())
      events.notify(
          t.getCreator(),
          t.getProject().getId(),
          id,
          "Status changed",
          t.getTitle() + " is now " + r.status());
    return view(t);
  }

  @Transactional
  public TaskDtos.View assign(UUID id, TaskDtos.Assignment r) {
    var t = require(id);
    version(t, r.version());
    var u = r.assigneeId() == null ? null : user(r.assigneeId());
    permissions.assign(t, u);
    var previous = t.getAssignee();
    t.setAssignee(u);
    tasks.saveAndFlush(t);
    events.record(
        t.getProject().getId(),
        id,
        "ASSIGNMENT_CHANGED",
        t.getTitle() + " assigned to " + (u == null ? "no one" : u.getFullName()));
    events.notify(u, t.getProject().getId(), id, "Task assigned", t.getTitle());
    if (previous != null && (u == null || !previous.getId().equals(u.getId())))
      events.notify(previous, t.getProject().getId(), id, "Task reassigned", t.getTitle());
    return view(t);
  }

  @Transactional
  public void requestReassignment(UUID id, String reason) {
    var t = require(id);
    permissions.edit(t);
    events.record(
        t.getProject().getId(), id, "REASSIGNMENT_REQUESTED", "Requested reassignment: " + reason);
    events.notify(
        t.getProject().getOwner(),
        t.getProject().getId(),
        id,
        "Reassignment requested",
        t.getTitle() + ": " + reason);
  }

  @Transactional
  public void delete(UUID id) {
    var t = require(id);
    projectRepo.lockById(t.getProject().getId()).orElseThrow();
    permissions.delete(t);
    events.record(t.getProject().getId(), id, "TASK_DELETED", "Deleted " + t.getTitle());
    attachmentStorage.deleteAfterCommit(attachments.keysForTask(id));
    tasks.delete(t);
  }

  public TaskDtos.View view(Task t) {
    var p = t.getProject();
    return new TaskDtos.View(
        t.getId(),
        p.getProjectKey() + "-" + t.getId().toString().substring(0, 5).toUpperCase(),
        t.getTitle(),
        t.getDescription(),
        p.getId(),
        p.getName(),
        p.getProjectKey(),
        UserDtos.Person.of(t.getCreator()),
        UserDtos.Person.of(t.getAssignee()),
        t.getTeam() == null ? null : t.getTeam().getId(),
        t.getTeam() == null ? null : t.getTeam().getName(),
        t.getStatus(),
        t.getPriority(),
        t.getDueDate(),
        t.getCreatedAt(),
        t.getUpdatedAt(),
        t.getCompletedAt(),
        t.getDueDate() != null
            && t.getDueDate().isBefore(LocalDate.now(clock))
            && (t.getStatus() == TaskStatus.TODO || t.getStatus() == TaskStatus.IN_PROGRESS),
        t.getVersion(),
        new TaskDtos.Capabilities(
            permissions.canEdit(t),
            permissions.canAssign(t),
            permissions.canSelfAssign(t),
            permissions.canDelete(t),
            permissions.canComment(t)));
  }

  private Team team(UUID id, Project p) {
    if (id == null) return null;
    var t = teams.findById(id).orElseThrow(() -> ApiException.badRequest("Team does not exist"));
    if (!t.getProject().getId().equals(p.getId()))
      throw ApiException.badRequest("Team must belong to this project");
    return t;
  }

  private User user(UUID id) {
    return users.findById(id).orElseThrow(() -> ApiException.badRequest("Assignee does not exist"));
  }

  private void version(Task t, long version) {
    if (t.getVersion() != version)
      throw ApiException.conflict("Task changed. Refresh and try again.");
  }
}
