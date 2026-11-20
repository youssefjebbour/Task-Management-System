package com.taskflow.collaboration;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RecurrenceRepository extends JpaRepository<RecurrenceRule, UUID> {
  Optional<RecurrenceRule> findByTemplateId(UUID taskId);

  @Query(
      value =
          "SELECT * FROM recurrence_rules WHERE active=true AND next_date<=:today ORDER BY next_date LIMIT 100 FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<RecurrenceRule> lockDue(LocalDate today);
}
