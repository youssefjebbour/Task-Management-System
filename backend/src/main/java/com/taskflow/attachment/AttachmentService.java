package com.taskflow.attachment;

import com.taskflow.activity.EventService;
import com.taskflow.auth.Actor;
import com.taskflow.common.ApiException;
import com.taskflow.project.*;
import com.taskflow.task.*;
import com.taskflow.user.UserDtos;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {
  private final AttachmentRepository attachments;
  private final AttachmentStorage storage;
  private final TaskRepository tasks;
  private final ProjectRepository projects;
  private final PermissionService permissions;
  private final Actor actor;
  private final EventService events;

  public record View(
      UUID id,
      String filename,
      String contentType,
      long sizeBytes,
      UserDtos.Person uploader,
      Instant createdAt,
      boolean canDelete) {}

  public record Download(String filename, byte[] bytes) {}

  private Task task(UUID id) {
    var t = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
    permissions.read(t.getProject());
    return t;
  }

  public List<View> list(UUID taskId) {
    task(taskId);
    return attachments.findByTaskIdOrderByCreatedAtAsc(taskId).stream().map(this::view).toList();
  }

  @Transactional
  public View upload(UUID taskId, MultipartFile file) {
    var t = task(taskId);
    projects.lockById(t.getProject().getId()).orElseThrow(() -> ApiException.notFound("Project"));
    permissions.comment(t);
    if (attachments.countByTaskId(taskId) >= 50)
      throw ApiException.badRequest("A task can have up to 50 attachments");
    var validated = AttachmentValidator.validate(file);
    var a = new Attachment();
    a.setTask(t);
    a.setUploader(actor.user());
    a.setFilename(validated.filename());
    a.setContentType(validated.contentType());
    a.setSizeBytes(validated.content().length);
    a.setStorageKey(storage.write(validated.content()));
    attachments.saveAndFlush(a);
    events.record(
        t.getProject().getId(), taskId, "ATTACHMENT_ADDED", "Attached " + a.getFilename());
    return view(a);
  }

  public Download download(UUID id) {
    var a = require(id);
    return new Download(a.getFilename(), storage.read(a.getStorageKey()));
  }

  @Transactional
  public void delete(UUID id) {
    var a = require(id);
    projects.lockById(a.getTask().getProject().getId()).orElseThrow();
    permissions.writable(a.getTask().getProject());
    if (!canDelete(a)) throw ApiException.forbidden();
    events.record(
        a.getTask().getProject().getId(),
        a.getTask().getId(),
        "ATTACHMENT_DELETED",
        "Removed " + a.getFilename());
    storage.deleteAfterCommit(List.of(a.getStorageKey()));
    attachments.delete(a);
  }

  private Attachment require(UUID id) {
    var a = attachments.findById(id).orElseThrow(() -> ApiException.notFound("Attachment"));
    permissions.read(a.getTask().getProject());
    return a;
  }

  private boolean canDelete(Attachment a) {
    return permissions.canComment(a.getTask())
        && (a.getUploader().getId().equals(actor.id()) || permissions.canDelete(a.getTask()));
  }

  private View view(Attachment a) {
    return new View(
        a.getId(),
        a.getFilename(),
        a.getContentType(),
        a.getSizeBytes(),
        UserDtos.Person.of(a.getUploader()),
        a.getCreatedAt(),
        canDelete(a));
  }
}
