package com.taskflow.task;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskFilter {
  @Size(max = 200)
  private String q = "";

  @Pattern(regexp = "all|mine|created|unassigned")
  private String scope = "mine";

  private UUID projectId;
  private UUID assigneeId;
  private UUID teamId;
  private UUID labelId;
  private TaskStatus status;
  private Priority priority;
  private LocalDate dueFrom;
  private LocalDate dueTo;
  private LocalDate createdFrom;
  private LocalDate createdTo;
  private Boolean overdue;
  private Boolean openOnly;

  @Pattern(regexp = "createdAt|updatedAt|dueDate|priority|title")
  private String sort = "dueDate";

  @Pattern(regexp = "asc|desc")
  private String direction = "asc";

  @Min(0)
  private int page = 0;

  @Min(1)
  @Max(100)
  private int size = 20;
}
