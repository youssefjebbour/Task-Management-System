package com.taskflow.collaboration;

import com.taskflow.activity.EventService;
import com.taskflow.auth.Actor;
import com.taskflow.common.*;
import com.taskflow.project.*;
import com.taskflow.task.*;
import com.taskflow.user.UserDtos;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollaborationService {
  private final TaskService tasks;
  private final ProjectService projects;
  private final ProjectRepository projectRepo;
  private final PermissionService permissions;
  private final CommentRepository comments;
  private final LabelRepository labels;
  private final TaskLabelRepository taskLabels;
  private final DependencyRepository dependencies;
  private final RecurrenceRepository recurrences;
  private final Actor actor;
  private final EventService events;
  private final Clock clock;

  public record CommentInput(@NotBlank @Size(max = 10000) String body) {}

  public record CommentView(
      UUID id,
      UserDtos.Person author,
      String body,
      Instant createdAt,
      Instant updatedAt,
      boolean canEdit,
      boolean canDelete) {}

  public record LabelInput(
      @NotBlank @Size(max = 50) String name, @Pattern(regexp = "#[0-9a-fA-F]{6}") String color) {}

  public record LabelView(UUID id, String name, String color) {
    static LabelView of(Label l) {
      return new LabelView(l.getId(), l.getName(), l.getColor());
    }
  }

  public record IdInput(@NotNull UUID id) {}

  public record DependencyView(UUID id, UUID taskId, String key, String title, TaskStatus status) {}

  public record Dependencies(List<DependencyView> blockedBy, List<DependencyView> blocking) {}

  public record RecurrenceInput(
      @NotNull Frequency frequency, @NotNull LocalDate nextDate, boolean active) {}

  public record RecurrenceView(UUID id, Frequency frequency, LocalDate nextDate, boolean active) {
    static RecurrenceView of(RecurrenceRule r) {
      return new RecurrenceView(r.getId(), r.getFrequency(), r.getNextDate(), r.isActive());
    }
  }

  public PageResponse<CommentView> comments(UUID taskId, int page, int size) {
    tasks.require(taskId);
    return PageResponse.of(
        comments
            .findByTaskIdOrderByCreatedAtDesc(taskId, PageRequest.of(page, size))
            .map(this::commentView));
  }

  @Transactional
  public CommentView addComment(UUID taskId, String body) {
    var t = tasks.require(taskId);
    permissions.comment(t);
    var c = new Comment();
    c.setTask(t);
    c.setAuthor(actor.user());
    c.setBody(body.trim());
    comments.save(c);
    events.record(t.getProject().getId(), taskId, "COMMENT_ADDED", "Commented on " + t.getTitle());
    events.notify(
        t.getAssignee(),
        t.getProject().getId(),
        taskId,
        "New comment",
        t.getTitle() + ": " + abbreviate(body));
    if (t.getAssignee() == null || !t.getCreator().getId().equals(t.getAssignee().getId()))
      events.notify(
          t.getCreator(),
          t.getProject().getId(),
          taskId,
          "New comment",
          t.getTitle() + ": " + abbreviate(body));
    return commentView(c);
  }

  @Transactional
  public CommentView editComment(UUID id, String body) {
    var c = comment(id);
    permissions.comment(c.getTask());
    if (!c.getAuthor().getId().equals(actor.id())) throw ApiException.forbidden();
    c.setBody(body.trim());
    comments.saveAndFlush(c);
    events.record(
        c.getTask().getProject().getId(),
        c.getTask().getId(),
        "COMMENT_EDITED",
        "Edited a comment");
    return commentView(c);
  }

  @Transactional
  public void deleteComment(UUID id) {
    var c = comment(id);
    permissions.comment(c.getTask());
    if (!c.getAuthor().getId().equals(actor.id()) && !permissions.manages(c.getTask().getProject()))
      throw ApiException.forbidden();
    events.record(
        c.getTask().getProject().getId(),
        c.getTask().getId(),
        "COMMENT_DELETED",
        "Removed a comment");
    comments.delete(c);
  }

  private Comment comment(UUID id) {
    var c = comments.findById(id).orElseThrow(() -> ApiException.notFound("Comment"));
    permissions.read(c.getTask().getProject());
    return c;
  }

  private CommentView commentView(Comment c) {
    boolean can = permissions.canComment(c.getTask()),
        own = c.getAuthor().getId().equals(actor.id());
    return new CommentView(
        c.getId(),
        UserDtos.Person.of(c.getAuthor()),
        c.getBody(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        can && own,
        can && (own || permissions.manages(c.getTask().getProject())));
  }

  public List<LabelView> labels(UUID projectId) {
    projects.require(projectId);
    return labels.findByProjectIdOrderByName(projectId).stream().map(LabelView::of).toList();
  }

  @Transactional
  public LabelView createLabel(UUID projectId, LabelInput r) {
    var p = projects.require(projectId);
    permissions.manage(p);
    permissions.writable(p);
    var l = new Label();
    l.setProject(p);
    l.setName(r.name().trim());
    if (r.color() != null) l.setColor(r.color());
    labels.save(l);
    events.record(projectId, null, "LABEL_CREATED", "Created label " + l.getName());
    return LabelView.of(l);
  }

  @Transactional
  public void deleteLabel(UUID id) {
    var l = labels.findById(id).orElseThrow(() -> ApiException.notFound("Label"));
    permissions.manage(l.getProject());
    permissions.writable(l.getProject());
    labels.delete(l);
  }

  public List<LabelView> taskLabels(UUID taskId) {
    tasks.require(taskId);
    return taskLabels.findByTaskId(taskId).stream().map(t -> LabelView.of(t.getLabel())).toList();
  }

  @Transactional
  public void attachLabel(UUID taskId, UUID labelId) {
    var t = tasks.require(taskId);
    permissions.edit(t);
    var l = labels.findById(labelId).orElseThrow(() -> ApiException.notFound("Label"));
    if (!l.getProject().getId().equals(t.getProject().getId()))
      throw ApiException.badRequest("Labels must belong to this project");
    if (!taskLabels.existsByTaskIdAndLabelId(taskId, labelId)) {
      var link = new TaskLabel();
      link.setTask(t);
      link.setLabel(l);
      taskLabels.save(link);
      events.record(t.getProject().getId(), taskId, "LABEL_ADDED", "Added label " + l.getName());
    }
  }

  @Transactional
  public void detachLabel(UUID taskId, UUID labelId) {
    var t = tasks.require(taskId);
    permissions.edit(t);
    taskLabels.findByTaskIdAndLabelId(taskId, labelId).ifPresent(taskLabels::delete);
    events.record(t.getProject().getId(), taskId, "LABEL_REMOVED", "Removed a label");
  }

  public Dependencies dependencies(UUID taskId) {
    tasks.require(taskId);
    return new Dependencies(
        dependencies.findByTaskId(taskId).stream()
            .map(d -> dependencyView(d, d.getBlocker()))
            .toList(),
        dependencies.findByBlockerId(taskId).stream()
            .map(d -> dependencyView(d, d.getTask()))
            .toList());
  }

  @Transactional
  public void addDependency(UUID taskId, UUID blockerId) {
    var t = tasks.require(taskId);
    permissions.edit(t);
    var blocker = tasks.require(blockerId);
    if (taskId.equals(blockerId) || !blocker.getProject().getId().equals(t.getProject().getId()))
      throw ApiException.badRequest("Dependencies must be different tasks in the same project");
    // Serialize graph mutations per project so two concurrent requests cannot create a cycle.
    projectRepo.lockById(t.getProject().getId()).orElseThrow();
    if (dependencies.existsByTaskIdAndBlockerId(taskId, blockerId))
      throw ApiException.conflict("This dependency already exists");
    if (dependencies.wouldCycle(taskId, blockerId))
      throw ApiException.badRequest("This dependency would create a cycle");
    if (t.getStatus() == TaskStatus.COMPLETED
        && t.getProject().isBlockIncompleteDependencies()
        && blocker.getStatus() != TaskStatus.COMPLETED)
      throw ApiException.badRequest("Reopen the task before adding an unfinished dependency");
    var d = new TaskDependency();
    d.setTask(t);
    d.setBlocker(blocker);
    dependencies.save(d);
    events.record(
        t.getProject().getId(), taskId, "DEPENDENCY_ADDED", "Blocked by " + blocker.getTitle());
  }

  @Transactional
  public void removeDependency(UUID id) {
    var d = dependencies.findById(id).orElseThrow(() -> ApiException.notFound("Dependency"));
    permissions.edit(d.getTask());
    projectRepo.lockById(d.getTask().getProject().getId()).orElseThrow();
    events.record(
        d.getTask().getProject().getId(),
        d.getTask().getId(),
        "DEPENDENCY_REMOVED",
        "Removed blocker " + d.getBlocker().getTitle());
    dependencies.delete(d);
  }

  private DependencyView dependencyView(TaskDependency d, Task t) {
    return new DependencyView(
        d.getId(),
        t.getId(),
        t.getProject().getProjectKey() + "-" + t.getId().toString().substring(0, 5).toUpperCase(),
        t.getTitle(),
        t.getStatus());
  }

  public RecurrenceView recurrence(UUID taskId) {
    tasks.require(taskId);
    return recurrences.findByTemplateId(taskId).map(RecurrenceView::of).orElse(null);
  }

  @Transactional
  public RecurrenceView recurrence(UUID taskId, RecurrenceInput r) {
    var t = tasks.require(taskId);
    permissions.manage(t.getProject());
    permissions.writable(t.getProject());
    if (r.nextDate().isBefore(LocalDate.now(clock)))
      throw ApiException.badRequest("First occurrence cannot be in the past");
    if (t.getRecurrenceId() != null)
      throw ApiException.badRequest(
          "Configure recurrence on the original template, not on a generated instance");
    var rule = recurrences.findByTemplateId(taskId).orElseGet(RecurrenceRule::new);
    rule.setTemplate(t);
    rule.setFrequency(r.frequency());
    rule.setNextDate(r.nextDate());
    rule.setAnchorDay(r.nextDate().getDayOfMonth());
    rule.setActive(r.active());
    recurrences.save(rule);
    events.record(
        t.getProject().getId(),
        taskId,
        "RECURRENCE_UPDATED",
        "Set recurring schedule: " + r.frequency());
    return RecurrenceView.of(rule);
  }

  private String abbreviate(String value) {
    return value.length() > 300 ? value.substring(0, 300) + "…" : value;
  }
}
