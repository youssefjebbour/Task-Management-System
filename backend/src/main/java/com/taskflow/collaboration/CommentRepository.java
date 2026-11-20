package com.taskflow.collaboration;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
  @EntityGraph(attributePaths = "author")
  Page<Comment> findByTaskIdOrderByCreatedAtDesc(UUID taskId, Pageable page);
}
