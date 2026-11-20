package com.taskflow.collaboration;

import com.taskflow.activity.EventService;
import com.taskflow.project.*;
import com.taskflow.task.*;
import com.taskflow.team.TeamMemberRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecurringTaskJob {
  private final RecurrenceRepository rules;
  private final TaskRepository tasks;
  private final ProjectMemberRepository members;
  private final TeamMemberRepository teams;
  private final EventService events;
  private final Clock clock;

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  @Transactional
  public void generateDue() {
    var today = LocalDate.now(clock);
    for (var rule : rules.lockDue(today)) {
      var source = rule.getTemplate();
      var project = source.getProject();
      if (project.getStatus() == ProjectStatus.ARCHIVED
          || project.getStatus() == ProjectStatus.COMPLETED
          || !project.getOwner().isActive()) continue;
      int generated = 0;
      while (!rule.getNextDate().isAfter(today) && generated++ < 31) {
        var occurrence = rule.getNextDate();
        if (!tasks.existsByRecurrenceIdAndOccurrenceDate(rule.getId(), occurrence)) {
          var task = new Task();
          task.setProject(project);
          task.setCreator(project.getOwner());
          task.setTitle(source.getTitle());
          task.setDescription(source.getDescription());
          task.setPriority(source.getPriority());
          task.setTeam(source.getTeam());
          task.setDueDate(occurrence);
          task.setRecurrenceId(rule.getId());
          task.setOccurrenceDate(occurrence);
          var assignee = source.getAssignee();
          if (assignee != null
              && assignee.isActive()
              && members
                  .findByProjectIdAndUserId(project.getId(), assignee.getId())
                  .filter(m -> m.getRole() != ProjectRole.VIEWER)
                  .isPresent()
              && (source.getTeam() == null
                  || teams.existsByTeamIdAndUserId(source.getTeam().getId(), assignee.getId())))
            task.setAssignee(assignee);
          tasks.saveAndFlush(task);
          events.recordAs(
              project.getId(),
              task.getId(),
              null,
              "TaskFlow",
              "RECURRING_TASK_CREATED",
              "Created recurring task " + task.getTitle());
          events.notifySystem(
              task.getAssignee(),
              project.getId(),
              task.getId(),
              "Recurring task ready",
              task.getTitle(),
              "recurrence:" + rule.getId() + ":" + occurrence);
        }
        rule.setNextDate(next(rule.getNextDate(), rule.getFrequency(), rule.getAnchorDay()));
      }
    }
  }

  public static LocalDate next(LocalDate current, Frequency frequency, int anchorDay) {
    return switch (frequency) {
      case DAILY -> current.plusDays(1);
      case WEEKLY -> current.plusWeeks(1);
      case MONTHLY -> {
        var month = YearMonth.from(current).plusMonths(1);
        yield month.atDay(Math.min(anchorDay, month.lengthOfMonth()));
      }
    };
  }
}
