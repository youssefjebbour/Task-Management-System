package com.taskflow.activity;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ActivityRepository extends JpaRepository<ActivityLog, UUID> {
  Page<ActivityLog> findByTaskIdOrderByCreatedAtDesc(UUID taskId, Pageable page);

  Page<ActivityLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable page);

  @Query(
      "select a from ActivityLog a where a.actorId=:id or a.projectId in (select m.project.id from ProjectMember m where m.user.id=:id) order by a.createdAt desc")
  Page<ActivityLog> feed(UUID id, Pageable page);
}
