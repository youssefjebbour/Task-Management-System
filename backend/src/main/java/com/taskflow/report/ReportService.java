package com.taskflow.report;

import com.taskflow.auth.Actor;
import com.taskflow.project.*;
import com.taskflow.task.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
  private final TaskRepository tasks;
  private final Actor actor;
  private final Clock clock;
  private final ProjectService projects;
  private final PermissionService permissions;
  private final ProjectMemberRepository members;

  public record MemberWork(UUID id, String name, long total, long completed) {}

  public record Summary(
      long total,
      long completed,
      long pending,
      long inProgress,
      long cancelled,
      long overdue,
      long dueToday,
      long dueThisWeek,
      double completionPercent,
      Map<String, Long> byStatus,
      Map<String, Long> byPriority,
      List<MemberWork> byMember) {}

  public Summary personal() {
    return summary(
        TaskSpecifications.accessible(actor.id(), actor.isAdmin())
            .and((r, q, c) -> c.equal(r.get("assignee").get("id"), actor.id())),
        null);
  }

  public Summary project(UUID id) {
    var p = projects.require(id);
    permissions.reports(p);
    Specification<Task> spec = (r, q, c) -> c.equal(r.get("project").get("id"), id);
    if (!permissions.manages(p) && permissions.role(p) == ProjectRole.TEAM_LEAD) {
      spec =
          spec.and(
              (r, q, c) -> {
                var sub = q.subquery(UUID.class);
                var m = sub.from(com.taskflow.team.TeamMember.class);
                sub.select(m.get("team").get("id"))
                    .where(c.equal(m.get("user").get("id"), actor.id()));
                return r.get("team").get("id").in(sub);
              });
    }
    return summary(spec, id);
  }

  private Summary summary(Specification<Task> scope, UUID projectId) {
    var statuses = new LinkedHashMap<String, Long>();
    for (var status : TaskStatus.values())
      statuses.put(
          status.name(), tasks.count(scope.and((r, q, c) -> c.equal(r.get("status"), status))));
    var priorities = new LinkedHashMap<String, Long>();
    for (var priority : Priority.values())
      priorities.put(
          priority.name(),
          tasks.count(scope.and((r, q, c) -> c.equal(r.get("priority"), priority))));
    Specification<Task> open =
        scope.and((r, q, c) -> r.get("status").in(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
    var today = LocalDate.now(clock);
    long overdue = tasks.count(open.and((r, q, c) -> c.lessThan(r.get("dueDate"), today)));
    long dueToday = tasks.count(open.and((r, q, c) -> c.equal(r.get("dueDate"), today)));
    var weekStart =
        today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    long dueWeek =
        tasks.count(
            open.and((r, q, c) -> c.between(r.get("dueDate"), weekStart, weekStart.plusDays(6))));
    long total = statuses.values().stream().mapToLong(Long::longValue).sum(),
        done = statuses.get("COMPLETED");
    var people = new ArrayList<MemberWork>();
    if (projectId != null)
      for (var m : members.findByProjectIdOrderByCreatedAtAsc(projectId)) {
        var s = scope.and((r, q, c) -> c.equal(r.get("assignee").get("id"), m.getUser().getId()));
        people.add(
            new MemberWork(
                m.getUser().getId(),
                m.getUser().getFullName(),
                tasks.count(s),
                tasks.count(s.and((r, q, c) -> c.equal(r.get("status"), TaskStatus.COMPLETED)))));
      }
    return new Summary(
        total,
        done,
        statuses.get("TODO"),
        statuses.get("IN_PROGRESS"),
        statuses.get("CANCELLED"),
        overdue,
        dueToday,
        dueWeek,
        total == 0 ? 0 : Math.round(1000.0 * done / total) / 10.0,
        statuses,
        priorities,
        people);
  }
}
