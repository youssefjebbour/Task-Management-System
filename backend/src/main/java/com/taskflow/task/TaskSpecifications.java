package com.taskflow.task;

import com.taskflow.project.ProjectMember;
import jakarta.persistence.criteria.Expression;
import java.time.*;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {
  private TaskSpecifications() {}

  public static Specification<Task> accessible(UUID actor, boolean admin) {
    return (root, query, cb) -> {
      if (admin) return cb.conjunction();
      var sub = query.subquery(UUID.class);
      var m = sub.from(ProjectMember.class);
      sub.select(m.get("project").get("id")).where(cb.equal(m.get("user").get("id"), actor));
      return root.get("project").get("id").in(sub);
    };
  }

  public static Specification<Task> filtered(TaskFilter f, UUID actor, boolean admin, Clock clock) {
    return accessible(actor, admin)
        .and(
            (root, query, cb) -> {
              var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
              if ("mine".equals(f.getScope()))
                predicates.add(cb.equal(root.get("assignee").get("id"), actor));
              if ("created".equals(f.getScope()))
                predicates.add(cb.equal(root.get("creator").get("id"), actor));
              if ("unassigned".equals(f.getScope()))
                predicates.add(cb.isNull(root.get("assignee")));
              if (f.getProjectId() != null)
                predicates.add(cb.equal(root.get("project").get("id"), f.getProjectId()));
              if (f.getAssigneeId() != null)
                predicates.add(cb.equal(root.get("assignee").get("id"), f.getAssigneeId()));
              if (f.getTeamId() != null)
                predicates.add(cb.equal(root.get("team").get("id"), f.getTeamId()));
              if (f.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), f.getStatus()));
              if (Boolean.TRUE.equals(f.getOpenOnly()))
                predicates.add(root.get("status").in(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
              if (f.getPriority() != null)
                predicates.add(cb.equal(root.get("priority"), f.getPriority()));
              if (f.getLabelId() != null) {
                var sub = query.subquery(UUID.class);
                var label = sub.from(com.taskflow.collaboration.TaskLabel.class);
                sub.select(label.get("task").get("id"))
                    .where(cb.equal(label.get("label").get("id"), f.getLabelId()));
                predicates.add(root.get("id").in(sub));
              }
              if (f.getQ() != null && !f.getQ().isBlank()) {
                String escaped =
                    f.getQ()
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '\\'),
                        cb.like(cb.lower(root.get("description")), "%" + escaped + "%", '\\')));
              }
              if (f.getDueFrom() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), f.getDueFrom()));
              if (f.getDueTo() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), f.getDueTo()));
              if (f.getCreatedFrom() != null)
                predicates.add(
                    cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        f.getCreatedFrom().atStartOfDay(clock.getZone()).toInstant()));
              if (f.getCreatedTo() != null)
                predicates.add(
                    cb.lessThan(
                        root.get("createdAt"),
                        f.getCreatedTo().plusDays(1).atStartOfDay(clock.getZone()).toInstant()));
              if (Boolean.TRUE.equals(f.getOverdue()))
                predicates.add(
                    cb.and(
                        cb.lessThan(root.get("dueDate"), LocalDate.now(clock)),
                        root.get("status").in(TaskStatus.TODO, TaskStatus.IN_PROGRESS)));
              if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                Expression<?> sort =
                    f.getSort().equals("priority")
                        ? cb.<Integer>selectCase()
                            .when(cb.equal(root.get("priority"), Priority.LOW), 0)
                            .when(cb.equal(root.get("priority"), Priority.MEDIUM), 1)
                            .when(cb.equal(root.get("priority"), Priority.HIGH), 2)
                            .otherwise(3)
                        : root.get(f.getSort());
                query.orderBy(
                    cb.asc(cb.<Integer>selectCase().when(cb.isNull(sort), 1).otherwise(0)),
                    "desc".equals(f.getDirection()) ? cb.desc(sort) : cb.asc(sort),
                    cb.asc(root.get("id")));
              }
              return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
  }
}
