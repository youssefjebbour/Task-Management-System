package com.taskflow.project;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
  @EntityGraph(attributePaths = {"user", "project"})
  Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

  @EntityGraph(attributePaths = "user")
  List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

  boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

  @Query(
      "select distinct m.user from ProjectMember m where m.project.id in (select mine.project.id from ProjectMember mine where mine.user.id=:id) order by m.user.fullName")
  List<com.taskflow.user.User> colleagues(UUID id);
}
