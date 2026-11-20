package com.taskflow.project;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
  @EntityGraph(attributePaths = "owner")
  @Query(
      "select p from Project p where (:admin=true or exists (select m.id from ProjectMember m where m.project=p and m.user.id=:userId)) and (:status is null or p.status=:status) and (lower(p.name) like lower(concat('%',:q,'%')) or lower(p.projectKey) like lower(concat('%',:q,'%')))")
  Page<Project> accessible(
      UUID userId, boolean admin, String q, ProjectStatus status, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Project p where p.id=:id")
  Optional<Project> lockById(UUID id);

  boolean existsByProjectKey(String projectKey);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = "DELETE FROM projects WHERE id=:id", nativeQuery = true)
  void deleteProjectGraph(UUID id);
}
