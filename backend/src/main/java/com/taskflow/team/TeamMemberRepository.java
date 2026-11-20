package com.taskflow.team;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
  boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

  @EntityGraph(attributePaths = "user")
  List<TeamMember> findByTeamId(UUID teamId);

  Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

  @Modifying
  @Query("delete from TeamMember m where m.user.id=:userId and m.team.project.id=:projectId")
  void removeFromProject(UUID userId, UUID projectId);
}
