package com.taskflow.collaboration;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DependencyRepository extends JpaRepository<TaskDependency, UUID> {
  @EntityGraph(attributePaths = {"blocker", "blocker.project"})
  List<TaskDependency> findByTaskId(UUID id);

  @EntityGraph(attributePaths = {"task", "task.project"})
  List<TaskDependency> findByBlockerId(UUID id);

  boolean existsByTaskIdAndBlockerId(UUID taskId, UUID blockerId);

  @Query(
      value =
          "WITH RECURSIVE ancestors(id) AS (SELECT blocker_id FROM task_dependencies WHERE task_id=:blocker UNION SELECT d.blocker_id FROM task_dependencies d JOIN ancestors a ON d.task_id=a.id) SELECT EXISTS(SELECT 1 FROM ancestors WHERE id=:task)",
      nativeQuery = true)
  boolean wouldCycle(UUID task, UUID blocker);

  @Query(
      "select count(d) from TaskDependency d where d.task.id=:id and d.blocker.status<>com.taskflow.task.TaskStatus.COMPLETED")
  long incompleteCount(UUID id);
}
