package com.taskflow.collaboration;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TaskLabelRepository extends JpaRepository<TaskLabel, UUID> {
  @EntityGraph(attributePaths = "label")
  List<TaskLabel> findByTaskId(UUID taskId);

  boolean existsByTaskIdAndLabelId(UUID taskId, UUID labelId);

  Optional<TaskLabel> findByTaskIdAndLabelId(UUID taskId, UUID labelId);
}
