package com.taskflow.notification;

import com.taskflow.activity.EventService;
import com.taskflow.task.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeadlineNotificationJob {
  private final TaskRepository tasks;
  private final EventService events;
  private final Clock clock;

  @Scheduled(fixedDelay = 3600000, initialDelay = 90000)
  @Transactional
  public void notifyDeadlines() {
    var today = LocalDate.now(clock);
    int page = 0;
    while (true) {
      var due =
          tasks.findAll(
              (r, q, c) ->
                  c.and(
                      r.get("status").in(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                      c.between(r.get("dueDate"), today, today.plusDays(1)),
                      c.isNotNull(r.get("assignee")),
                      c.notEqual(
                          r.get("project").get("status"),
                          com.taskflow.project.ProjectStatus.ARCHIVED)),
              PageRequest.of(page++, 100, org.springframework.data.domain.Sort.by("id")));
      for (var t : due)
        events.notifySystem(
            t.getAssignee(),
            t.getProject().getId(),
            t.getId(),
            t.getDueDate().equals(today) ? "Due today" : "Due tomorrow",
            t.getTitle(),
            "deadline:"
                + t.getId()
                + ":"
                + t.getAssignee().getId()
                + ":"
                + t.getDueDate()
                + ":"
                + today);
      if (!due.hasNext()) break;
    }
  }
}
