package com.taskflow.team;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {
  List<Team> findByProjectIdOrderByName(UUID projectId);
}
