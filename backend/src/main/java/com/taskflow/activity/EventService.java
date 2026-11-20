package com.taskflow.activity;

import com.taskflow.auth.Actor;
import com.taskflow.notification.*;
import com.taskflow.user.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventService {
  private final Actor actor;
  private final ActivityRepository logs;
  private final NotificationRepository notifications;

  public void record(UUID projectId, UUID taskId, String action, String description) {
    var u = actor.user();
    recordAs(projectId, taskId, u.getId(), u.getFullName(), action, description);
  }

  public void recordAs(
      UUID projectId, UUID taskId, UUID actorId, String name, String action, String description) {
    var log = new ActivityLog();
    log.setProjectId(projectId);
    log.setTaskId(taskId);
    log.setActorId(actorId);
    log.setActorName(name);
    log.setAction(action);
    log.setDescription(description);
    logs.save(log);
  }

  public void notify(User recipient, UUID projectId, UUID taskId, String title, String message) {
    if (recipient == null || !recipient.isActive() || recipient.getId().equals(actor.id())) return;
    notifySystem(recipient, projectId, taskId, title, message, null);
  }

  public void notifySystem(
      User recipient, UUID projectId, UUID taskId, String title, String message, String dedupKey) {
    if (recipient == null
        || !recipient.isActive()
        || (dedupKey != null && notifications.existsByDedupKey(dedupKey))) return;
    var n = new Notification();
    n.setRecipient(recipient);
    n.setProjectId(projectId);
    n.setTaskId(taskId);
    n.setTitle(title);
    n.setMessage(message);
    n.setDedupKey(dedupKey);
    notifications.save(n);
  }
}
