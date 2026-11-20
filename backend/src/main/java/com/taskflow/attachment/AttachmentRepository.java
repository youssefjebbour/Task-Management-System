package com.taskflow.attachment;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
  @EntityGraph(attributePaths = {"uploader", "task", "task.project"})
  List<Attachment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

  long countByTaskId(UUID taskId);

  @Query("select a.storageKey from Attachment a where a.task.id=:taskId")
  List<String> keysForTask(UUID taskId);

  @Query("select a.storageKey from Attachment a where a.task.project.id=:projectId")
  List<String> keysForProject(UUID projectId);
}
