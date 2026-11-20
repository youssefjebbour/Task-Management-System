package com.taskflow.user;

import com.taskflow.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {
  private final UserService service;

  @GetMapping("/me")
  UserDtos.Profile me() {
    return service.me();
  }

  @PutMapping("/me")
  UserDtos.Profile update(@Valid @RequestBody UserDtos.Update r) {
    return service.update(r);
  }

  @PostMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void password(@Valid @RequestBody UserDtos.Password r) {
    service.password(r);
  }

  @GetMapping("/directory")
  List<UserDtos.Person> directory() {
    return service.directory();
  }

  @GetMapping
  PageResponse<UserDtos.Profile> list(
      @RequestParam(defaultValue = "") @Size(max = 200) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(q, page, size);
  }

  @PatchMapping("/{id}")
  UserDtos.Profile admin(@PathVariable UUID id, @Valid @RequestBody UserDtos.AdminUpdate r) {
    return service.admin(id, r);
  }
}
