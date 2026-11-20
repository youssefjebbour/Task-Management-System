package com.taskflow;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.taskflow.user.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.jwt-secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItaW50ZWdyYXRpb24tdGVzdHMtMzItYnl0ZXM=",
      "app.seed-enabled=false",
      "app.auth-rate-limit=200",
      "management.health.mail.enabled=false"
    })
@Testcontainers
class CoreIntegrationTest {
  static {
    System.setProperty("api.version", "1.44");
  }

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired com.taskflow.collaboration.RecurringTaskJob recurring;
  @Autowired com.taskflow.attachment.AttachmentRepository attachments;
  @Autowired com.taskflow.attachment.AttachmentStorage storage;

  @org.springframework.test.context.DynamicPropertySource
  static void uploadDirectory(org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add(
        "app.uploads",
        () -> System.getProperty("java.io.tmpdir") + "/taskflow-test-" + postgres.getContainerId());
  }

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  org.springframework.mail.javamail.JavaMailSender mail;

  record Account(String email, String id, String token, String cookie) {}

  Account register(String prefix) {
    String email = prefix + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
    var response =
        call(
            HttpMethod.POST,
            "/auth/register",
            null,
            Map.of(
                "email",
                email,
                "username",
                email.split("@")[0],
                "fullName",
                prefix + " Tester",
                "password",
                "Testing-Password-123!"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var body = response.getBody();
    return new Account(
        email,
        body.path("user").path("id").asText(),
        body.path("accessToken").asText(),
        response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0]);
  }

  ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
    var headers = new HttpHeaders();
    if (token != null) headers.setBearerAuth(token);
    if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  String project(Account owner) {
    var response =
        call(
            HttpMethod.POST,
            "/projects",
            owner.token(),
            Map.of(
                "name",
                "Permission testing",
                "projectKey",
                "T" + UUID.randomUUID().toString().substring(0, 7)));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().path("id").asText();
  }

  void add(Account owner, String project, Account member, String role) {
    assertThat(
            call(
                    HttpMethod.POST,
                    "/projects/" + project + "/members",
                    owner.token(),
                    Map.of("email", member.email(), "role", role))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  JsonNode task(Account owner, String project, String assignee, String title) {
    var body = new HashMap<String, Object>();
    body.put("projectId", project);
    body.put("title", title);
    body.put("priority", "HIGH");
    if (assignee != null) body.put("assigneeId", assignee);
    var response = call(HttpMethod.POST, "/tasks", owner.token(), body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  @Test
  void registrationValidationUniquenessAndLogin() {
    Account account = register("auth");
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/login",
                    null,
                    Map.of("email", account.email(), "password", "Testing-Password-123!"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/login",
                    null,
                    Map.of("email", account.email(), "password", "incorrect"))
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/register",
                    null,
                    Map.of(
                        "email",
                        account.email(),
                        "username",
                        "unique_user",
                        "fullName",
                        "Duplicate",
                        "password",
                        "Testing-Password-123!"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/register",
                    null,
                    Map.of(
                        "email", "invalid", "username", "x", "fullName", "", "password", "short"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(call(HttpMethod.GET, "/users/me", null, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            call(HttpMethod.GET, "/users/me", account.token(), null).getBody().has("passwordHash"))
        .isFalse();
  }

  @Test
  void refreshRotatesAndReplayRevokesTheWholeFamily() {
    var user = register("refresh");
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, user.cookie());
    var first =
        http.exchange(
            "/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), JsonNode.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    String next = first.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];
    assertThat(next).isNotEqualTo(user.cookie());
    assertThat(
            http.exchange(
                    "/api/v1/auth/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    headers.set(HttpHeaders.COOKIE, next);
    assertThat(
            http.exchange(
                    "/api/v1/auth/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void projectIsolationMembershipRolesAndOwnership() {
    var owner = register("owner");
    var other = register("other");
    var manager = register("manager");
    String project = project(owner);
    assertThat(call(HttpMethod.GET, "/projects/" + project, other.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(HttpMethod.GET, "/projects", other.token(), null)
                .getBody()
                .path("totalElements")
                .asInt())
        .isZero();
    add(owner, project, manager, "MANAGER");
    assertThat(
            call(
                    HttpMethod.POST,
                    "/projects/" + project + "/members",
                    manager.token(),
                    Map.of("email", other.email(), "role", "MANAGER"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    HttpMethod.DELETE,
                    "/projects/" + project + "/members/" + owner.id(),
                    manager.token(),
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/projects/" + project + "/transfer",
                    owner.token(),
                    Map.of("userId", manager.id()))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(HttpMethod.GET, "/projects/" + project, owner.token(), null)
                .getBody()
                .path("role")
                .asText())
        .isEqualTo("MANAGER");
  }

  @Test
  void membersCannotAssignOthersAndViewersCannotMutate() {
    var owner = register("taskowner");
    var member = register("member");
    var viewer = register("viewer");
    var outsider = register("outsider");
    String project = project(owner);
    add(owner, project, member, "MEMBER");
    add(owner, project, viewer, "VIEWER");
    JsonNode task = task(owner, project, member.id(), "Assigned work");
    String id = task.path("id").asText();
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + id + "/assignee",
                    member.token(),
                    Map.of("assigneeId", owner.id(), "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + id + "/status",
                    viewer.token(),
                    Map.of("status", "COMPLETED", "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + id + "/assignee",
                    owner.token(),
                    Map.of("assigneeId", outsider.id(), "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + id + "/assignee",
                    owner.token(),
                    Map.of("assigneeId", viewer.id(), "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var done =
        call(
            HttpMethod.PATCH,
            "/tasks/" + id + "/status",
            member.token(),
            Map.of("status", "COMPLETED", "version", 0));
    assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(done.getBody().path("completedAt").isTextual()).isTrue();
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + id + "/status",
                    member.token(),
                    Map.of("status", "TODO", "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var reopen =
        call(
            HttpMethod.PATCH,
            "/tasks/" + id + "/status",
            member.token(),
            Map.of("status", "TODO", "version", done.getBody().path("version").asLong()));
    assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reopen.getBody().path("completedAt").isNull()).isTrue();
    JsonNode unassigned = task(owner, project, null, "Unassigned work");
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + unassigned.path("id").asText() + "/assignee",
                    member.token(),
                    Map.of("assigneeId", member.id(), "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void databaseFilteringPaginationUpdatesAndDeletion() {
    var owner = register("filter");
    String project = project(owner);
    for (int i = 0; i < 4; i++) task(owner, project, owner.id(), "Review " + i);
    var response =
        call(
            HttpMethod.GET,
            "/tasks?scope=mine&projectId="
                + project
                + "&priority=HIGH&q=Review&size=2&page=1&sort=title",
            owner.token(),
            null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("totalElements").asInt()).isEqualTo(4);
    assertThat(response.getBody().path("content").size()).isEqualTo(2);
    assertThat(response.getBody().path("content").get(0).path("title").asText())
        .isEqualTo("Review 2");
    var item = response.getBody().path("content").get(0);
    String id = item.path("id").asText();
    assertThat(
            call(
                    HttpMethod.PUT,
                    "/tasks/" + id,
                    owner.token(),
                    Map.of(
                        "title",
                        "Edited title",
                        "priority",
                        "LOW",
                        "version",
                        item.path("version").asLong()))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(call(HttpMethod.DELETE, "/tasks/" + id, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(call(HttpMethod.GET, "/tasks/" + id, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.GET, "/tasks?size=101", owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(HttpMethod.GET, "/reports/personal", owner.token(), null)
                .getBody()
                .path("total")
                .asInt())
        .isEqualTo(3);
  }

  @Test
  void inactiveAccountsCannotUseExistingAccessTokens() {
    var account = register("inactive");
    var user = users.findById(UUID.fromString(account.id())).orElseThrow();
    user.setActive(false);
    users.saveAndFlush(user);
    assertThat(call(HttpMethod.GET, "/users/me", account.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void commentsHaveAuthorAndModeratorPermissions() {
    var owner = register("commentOwner");
    var member = register("commenter");
    var viewer = register("reader");
    String p = project(owner);
    add(owner, p, member, "MEMBER");
    add(owner, p, viewer, "VIEWER");
    var t = task(owner, p, member.id(), "Discuss this work");
    String id = t.path("id").asText();
    var comment =
        call(
            HttpMethod.POST,
            "/tasks/" + id + "/comments",
            member.token(),
            Map.of("body", "Ready for review"));
    assertThat(comment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String cid = comment.getBody().path("id").asText();
    assertThat(
            call(
                    HttpMethod.POST,
                    "/tasks/" + id + "/comments",
                    viewer.token(),
                    Map.of("body", "Not allowed"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    HttpMethod.PUT,
                    "/comments/" + cid,
                    owner.token(),
                    Map.of("body", "Cannot rewrite another author"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    HttpMethod.PUT,
                    "/comments/" + cid,
                    member.token(),
                    Map.of("body", "Updated review notes"))
                .getBody()
                .path("body")
                .asText())
        .isEqualTo("Updated review notes");
    assertThat(
            call(HttpMethod.GET, "/tasks/" + id + "/comments", viewer.token(), null)
                .getBody()
                .path("totalElements")
                .asInt())
        .isEqualTo(1);
    assertThat(call(HttpMethod.DELETE, "/comments/" + cid, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void dependenciesRejectCyclesAndEnforceCompletionPolicy() {
    var owner = register("dependency");
    String p = project(owner);
    var a = task(owner, p, owner.id(), "Task A");
    var b = task(owner, p, owner.id(), "Task B");
    var c = task(owner, p, owner.id(), "Task C");
    String aid = a.path("id").asText(), bid = b.path("id").asText(), cid = c.path("id").asText();
    assertThat(
            call(
                    HttpMethod.POST,
                    "/tasks/" + aid + "/dependencies",
                    owner.token(),
                    Map.of("id", bid))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/tasks/" + bid + "/dependencies",
                    owner.token(),
                    Map.of("id", cid))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/tasks/" + cid + "/dependencies",
                    owner.token(),
                    Map.of("id", aid))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/tasks/" + aid + "/dependencies",
                    owner.token(),
                    Map.of("id", aid))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + aid + "/status",
                    owner.token(),
                    Map.of("status", "COMPLETED", "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    for (String id : List.of(cid, bid, aid))
      assertThat(
              call(
                      HttpMethod.PATCH,
                      "/tasks/" + id + "/status",
                      owner.token(),
                      Map.of("status", "COMPLETED", "version", 0))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
  }

  @Test
  void labelsAreScopedAndFilterPersistedTasks() {
    var owner = register("labels");
    String p = project(owner), other = project(owner);
    var t = task(owner, p, owner.id(), "Tagged task");
    String tid = t.path("id").asText();
    var response =
        call(
            HttpMethod.POST,
            "/projects/" + p + "/labels",
            owner.token(),
            Map.of("name", "Accessibility", "color", "#2563b8"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String label = response.getBody().path("id").asText();
    assertThat(
            call(HttpMethod.POST, "/tasks/" + tid + "/labels", owner.token(), Map.of("id", label))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var otherTask = task(owner, other, owner.id(), "Different project");
    assertThat(
            call(
                    HttpMethod.POST,
                    "/tasks/" + otherTask.path("id").asText() + "/labels",
                    owner.token(),
                    Map.of("id", label))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(HttpMethod.GET, "/tasks?scope=all&labelId=" + label, owner.token(), null)
                .getBody()
                .path("totalElements")
                .asInt())
        .isEqualTo(1);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/projects/" + p + "/labels",
                    owner.token(),
                    Map.of("name", "Unsafe color", "color", "javascript:bad"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void teamLeadsCanAssignOnlyInsideTheirTeam() {
    var owner = register("leadOwner");
    var lead = register("lead");
    var inside = register("inside");
    var outside = register("outside");
    String p = project(owner);
    add(owner, p, lead, "TEAM_LEAD");
    add(owner, p, inside, "MEMBER");
    add(owner, p, outside, "MEMBER");
    var team =
        call(
            HttpMethod.POST,
            "/teams",
            owner.token(),
            Map.of("projectId", p, "name", "Scoped team"));
    assertThat(team.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String teamId = team.getBody().path("id").asText();
    for (var person : List.of(lead, inside))
      assertThat(
              call(
                      HttpMethod.POST,
                      "/teams/" + teamId + "/members",
                      owner.token(),
                      Map.of("userId", person.id()))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    var created =
        call(
            HttpMethod.POST,
            "/tasks",
            lead.token(),
            Map.of(
                "projectId",
                p,
                "title",
                "Team-owned task",
                "priority",
                "HIGH",
                "teamId",
                teamId,
                "assigneeId",
                inside.id()));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asText();
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + id + "/assignee",
                    lead.token(),
                    Map.of("assigneeId", outside.id(), "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var unscoped = task(owner, p, outside.id(), "Outside team");
    assertThat(
            call(
                    HttpMethod.PATCH,
                    "/tasks/" + unscoped.path("id").asText() + "/status",
                    lead.token(),
                    Map.of("status", "COMPLETED", "version", 0))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    HttpMethod.DELETE,
                    "/teams/" + teamId + "/members/" + inside.id(),
                    owner.token(),
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void recurrenceGeneratesOnePersistedOccurrenceAndDoesNotDuplicate() {
    var owner = register("recurring");
    String p = project(owner);
    var t = task(owner, p, owner.id(), "Daily review");
    String tid = t.path("id").asText();
    var response =
        call(
            HttpMethod.PUT,
            "/tasks/" + tid + "/recurrence",
            owner.token(),
            Map.of(
                "frequency",
                "DAILY",
                "nextDate",
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString(),
                "active",
                true));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    recurring.generateDue();
    recurring.generateDue();
    var list = call(HttpMethod.GET, "/tasks?scope=all&projectId=" + p, owner.token(), null);
    assertThat(list.getBody().path("totalElements").asInt()).isEqualTo(2);
  }

  @Test
  void passwordRecoveryIsSingleUseAndRevokesSessions() {
    var account = register("reset");
    assertThat(
            call(HttpMethod.POST, "/auth/forgot-password", null, Map.of("email", account.email()))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var captured =
        org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
    org.mockito.Mockito.verify(mail, org.mockito.Mockito.timeout(3000)).send(captured.capture());
    String text = captured.getValue().getText();
    String token = text.substring(text.indexOf("token=") + 6).split("\\s")[0];
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/reset-password",
                    null,
                    Map.of("token", token, "password", "New-Password-9876!"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/reset-password",
                    null,
                    Map.of("token", token, "password", "New-Password-9876!"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(call(HttpMethod.GET, "/users/me", account.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/login",
                    null,
                    Map.of("email", account.email(), "password", "New-Password-9876!"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/auth/forgot-password",
                    null,
                    Map.of("email", "unknown@example.test"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void notificationsBelongOnlyToTheirRecipient() {
    var owner = register("notifyOwner");
    var member = register("notifyMember");
    String p = project(owner);
    add(owner, p, member, "MEMBER");
    task(owner, p, member.id(), "Assigned notification");
    var list = call(HttpMethod.GET, "/notifications", member.token(), null).getBody();
    assertThat(list.path("totalElements").asInt()).isEqualTo(2);
    String id = list.path("content").get(0).path("id").asText();
    assertThat(
            call(HttpMethod.PATCH, "/notifications/" + id, owner.token(), Map.of("read", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(HttpMethod.PATCH, "/notifications/" + id, member.token(), Map.of("read", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            call(HttpMethod.GET, "/notifications/unread-count", member.token(), null)
                .getBody()
                .path("count")
                .asInt())
        .isEqualTo(1);
  }

  @Test
  void archivedProjectDeletionCascadesChildrenAndKeepsAuditHistory() {
    var owner = register("deletion");
    String p = project(owner);
    var t = task(owner, p, owner.id(), "Temporary task");
    String id = t.path("id").asText();
    call(
        HttpMethod.POST,
        "/tasks/" + id + "/comments",
        owner.token(),
        Map.of("body", "Preserved until project deletion"));
    assertThat(call(HttpMethod.DELETE, "/projects/" + p, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var current = call(HttpMethod.GET, "/projects/" + p, owner.token(), null).getBody();
    var update = mapper.convertValue(current, Map.class);
    update.put("status", "ARCHIVED");
    assertThat(call(HttpMethod.PUT, "/projects/" + p, owner.token(), update).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(call(HttpMethod.DELETE, "/projects/" + p, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(call(HttpMethod.GET, "/tasks/" + id, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(HttpMethod.GET, "/activity", owner.token(), null)
                .getBody()
                .path("content")
                .toString())
        .contains("PROJECT_DELETED");
  }

  ResponseEntity<JsonNode> upload(Account account, String taskId, String filename, byte[] bytes) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(account.token());
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
    body.add(
        "file",
        new org.springframework.core.io.ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return filename;
          }
        });
    return http.exchange(
        "/api/v1/tasks/" + taskId + "/attachments",
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        JsonNode.class);
  }

  @Test
  void attachmentsValidateContentAndEnforceMembership() {
    var owner = register("filesOwner");
    var member = register("filesMember");
    var viewer = register("filesViewer");
    var outsider = register("filesOutside");
    String p = project(owner);
    add(owner, p, member, "MEMBER");
    add(owner, p, viewer, "VIEWER");
    String tid = task(owner, p, member.id(), "File review").path("id").asText();
    byte[] bytes = "Release checklist\nVerified".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var response = upload(member, tid, "checklist.txt", bytes);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = response.getBody().path("id").asText();
    assertThat(response.getBody().has("storageKey")).isFalse();
    assertThat(response.getBody().path("sizeBytes").asInt()).isEqualTo(bytes.length);
    var headers = new HttpHeaders();
    headers.setBearerAuth(viewer.token());
    var file =
        http.exchange(
            "/api/v1/attachments/" + id + "/download",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            byte[].class);
    assertThat(file.getBody()).isEqualTo(bytes);
    assertThat(file.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;");
    assertThat(file.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(
            call(HttpMethod.GET, "/attachments/" + id + "/download", outsider.token(), null)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(upload(viewer, tid, "no.txt", bytes).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(upload(owner, tid, "spoof.png", bytes).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(upload(owner, tid, "unsafe.html", bytes).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(upload(owner, tid, "../escape.txt", bytes).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(upload(owner, tid, "binary.txt", new byte[] {0, 1, 2}).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(upload(owner, tid, "empty.txt", new byte[0]).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(upload(owner, tid, "huge.txt", new byte[10 * 1024 * 1024 + 1]).getStatusCode())
        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(call(HttpMethod.DELETE, "/attachments/" + id, viewer.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var key = attachments.findById(UUID.fromString(id)).orElseThrow().getStorageKey();
    assertThat(java.nio.file.Files.exists(storage.path(key))).isTrue();
    assertThat(call(HttpMethod.DELETE, "/attachments/" + id, member.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(java.nio.file.Files.exists(storage.path(key))).isFalse();
  }

  @Test
  void deletingTasksAndProjectsRemovesAttachmentFiles() {
    var owner = register("fileCleanup");
    String p = project(owner);
    String tid = task(owner, p, owner.id(), "Temporary attachments").path("id").asText();
    String id = upload(owner, tid, "notes.md", "# Notes".getBytes()).getBody().path("id").asText();
    String key = attachments.findById(UUID.fromString(id)).orElseThrow().getStorageKey();
    assertThat(call(HttpMethod.DELETE, "/tasks/" + tid, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(java.nio.file.Files.exists(storage.path(key))).isFalse();
    assertThat(attachments.existsById(UUID.fromString(id))).isFalse();
    tid = task(owner, p, owner.id(), "Project cleanup").path("id").asText();
    id = upload(owner, tid, "notes.md", "# Notes".getBytes()).getBody().path("id").asText();
    key = attachments.findById(UUID.fromString(id)).orElseThrow().getStorageKey();
    var update =
        mapper.convertValue(
            call(HttpMethod.GET, "/projects/" + p, owner.token(), null).getBody(), Map.class);
    update.put("status", "ARCHIVED");
    assertThat(call(HttpMethod.PUT, "/projects/" + p, owner.token(), update).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(upload(owner, tid, "blocked.txt", "No".getBytes()).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(call(HttpMethod.DELETE, "/projects/" + p, owner.token(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(java.nio.file.Files.exists(storage.path(key))).isFalse();
  }

  @Test
  void weeklyReportsUseCalendarWeekAndReconcileStatusTotals() {
    var owner = register("weekReport");
    String p = project(owner);
    var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    var monday =
        today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    for (var date : List.of(monday, monday.plusDays(6), monday.plusDays(7))) {
      assertThat(
              call(
                      HttpMethod.POST,
                      "/tasks",
                      owner.token(),
                      Map.of(
                          "projectId",
                          p,
                          "title",
                          "Calendar week task",
                          "priority",
                          "MEDIUM",
                          "assigneeId",
                          owner.id(),
                          "dueDate",
                          date.toString()))
                  .getStatusCode())
          .isEqualTo(HttpStatus.CREATED);
    }
    var report = call(HttpMethod.GET, "/reports/personal", owner.token(), null).getBody();
    assertThat(report.path("dueThisWeek").asInt()).isEqualTo(2);
    assertThat(report.path("total").asInt()).isEqualTo(3);
    assertThat(report.path("byStatus").path("TODO").asInt()).isEqualTo(3);
    assertThat(report.path("byPriority").path("MEDIUM").asInt()).isEqualTo(3);
  }
}
