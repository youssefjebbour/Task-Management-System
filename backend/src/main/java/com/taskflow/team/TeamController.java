package com.taskflow.team;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {
  private final TeamService service;

  @GetMapping
  List<TeamService.View> list(@RequestParam UUID projectId) {
    return service.list(projectId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TeamService.View create(@Valid @RequestBody TeamService.Create r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  TeamService.View rename(@PathVariable UUID id, @Valid @RequestBody TeamService.Rename r) {
    return service.rename(id, r.name());
  }

  @PostMapping("/{id}/members")
  TeamService.View add(@PathVariable UUID id, @Valid @RequestBody TeamService.AddMember r) {
    return service.add(id, r.userId());
  }

  @DeleteMapping("/{id}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@PathVariable UUID id, @PathVariable UUID userId) {
    service.remove(id, userId);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
