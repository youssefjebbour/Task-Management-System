package com.taskflow.collaboration;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface LabelRepository extends JpaRepository<Label, UUID> {
  List<Label> findByProjectIdOrderByName(UUID projectId);
}
