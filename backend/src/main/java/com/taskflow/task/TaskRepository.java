package com.taskflow.task;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {
  @Override
  @EntityGraph(attributePaths = {"project", "creator", "assignee", "team"})
  Page<Task> findAll(Specification<Task> spec, Pageable page);

  long countByProjectId(UUID projectId);

  long countByProjectIdAndStatus(UUID projectId, TaskStatus status);

  long countByProjectIdAndAssigneeId(UUID projectId, UUID assigneeId);

  boolean existsByRecurrenceIdAndOccurrenceDate(UUID id, java.time.LocalDate date);

  @Modifying
  @Query(
      "update Task t set t.assignee=null, t.version=t.version+1 where t.project.id=:projectId and t.assignee.id=:userId")
  void unassignMember(UUID projectId, UUID userId);
}
