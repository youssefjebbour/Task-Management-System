package com.taskflow.config;

import com.taskflow.activity.*;
import com.taskflow.notification.*;
import com.taskflow.project.*;
import com.taskflow.task.*;
import com.taskflow.team.*;
import com.taskflow.user.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicitly opt-in sample records for an empty local database, never frontend fixtures. */
@Component
@ConditionalOnProperty(name = "app.seed-enabled", havingValue = "true")
public class DevelopmentSeed implements ApplicationRunner {
  private final UserRepository users;
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final TaskRepository tasks;
  private final TeamRepository teams;
  private final TeamMemberRepository teamMembers;
  private final ActivityRepository logs;
  private final NotificationRepository notifications;
  private final PasswordEncoder encoder;
  private final String password;
  private final Clock clock;

  public DevelopmentSeed(
      UserRepository users,
      ProjectRepository projects,
      ProjectMemberRepository members,
      TaskRepository tasks,
      TeamRepository teams,
      TeamMemberRepository teamMembers,
      ActivityRepository logs,
      NotificationRepository notifications,
      PasswordEncoder encoder,
      @Value("${app.seed-password}") String password,
      Clock clock) {
    this.users = users;
    this.projects = projects;
    this.members = members;
    this.tasks = tasks;
    this.teams = teams;
    this.teamMembers = teamMembers;
    this.logs = logs;
    this.notifications = notifications;
    this.encoder = encoder;
    this.password = password;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.count() != 0) return;
    if (password.length() < 12)
      throw new IllegalStateException(
          "Development seeding requires SEED_PASSWORD with at least 12 characters");
    String hash = encoder.encode(password);
    String[][] names = {
      {"Maya Chen", "maya", "Product designer"},
      {"Leo Martins", "leo", "Frontend engineer"},
      {"Nora Williams", "nora", "Product manager"},
      {"Eli Brooks", "eli", "Engineering lead"},
      {"Iris Patel", "iris", "Project stakeholder"},
      {"Platform Admin", "admin", "Workspace administrator"}
    };
    var people = new ArrayList<User>();
    for (var n : names) {
      var u = new User();
      u.setFullName(n[0]);
      u.setUsername(n[1]);
      u.setEmail(n[1] + "@taskflow.local");
      u.setJobTitle(n[2]);
      u.setPasswordHash(hash);
      if (n[1].equals("admin")) u.setSystemRole(SystemRole.ADMIN);
      users.save(u);
      people.add(u);
    }
    String[][] projectNames = {
      {
        "Website redesign",
        "WEB",
        "A clearer, more accessible home for our product. From discovery to the final launch."
      },
      {
        "Mobile experience",
        "MOB",
        "Make the everyday workflow feel effortless on a smaller screen."
      },
      {
        "Design system",
        "DS",
        "Shared patterns and accessible components that keep the product consistent."
      }
    };
    String[][] titles = {
      {
        "Map the onboarding journey",
        "Review homepage wireframes",
        "Build the project navigation",
        "Audit keyboard accessibility",
        "Define the content hierarchy",
        "Document responsive breakpoints",
        "Design the empty states",
        "Implement account settings",
        "Review the search interaction",
        "Prepare the release checklist",
        "Refine the task detail drawer",
        "Validate form error messages",
        "Connect the notification service",
        "Run the usability review"
      },
      {
        "Explore the mobile task list",
        "Prototype the filter sheet",
        "Review touch target sizes",
        "Test calendar navigation",
        "Document offline error states",
        "Polish the mobile sidebar"
      },
      {
        "Create the typography scale",
        "Document button variants",
        "Review semantic color tokens",
        "Build the status indicators",
        "Test dark theme contrast",
        "Publish component guidelines"
      }
    };
    var today = LocalDate.now(clock);
    ProjectRole[] roles = {
      ProjectRole.OWNER,
      ProjectRole.MEMBER,
      ProjectRole.MANAGER,
      ProjectRole.TEAM_LEAD,
      ProjectRole.VIEWER
    };
    for (int pi = 0; pi < projectNames.length; pi++) {
      var p = new Project();
      p.setName(projectNames[pi][0]);
      p.setProjectKey(projectNames[pi][1]);
      p.setDescription(projectNames[pi][2]);
      p.setStatus(pi == 1 ? ProjectStatus.PLANNING : ProjectStatus.ACTIVE);
      p.setOwner(people.get(0));
      p.setStartDate(today.minusDays(14));
      p.setDeadline(today.plusDays(30 + pi * 10));
      projects.save(p);
      for (int i = 0; i < 5; i++) {
        var m = new ProjectMember();
        m.setProject(p);
        m.setUser(people.get(i));
        m.setRole(roles[i]);
        members.save(m);
      }
      var team = new Team();
      team.setProject(p);
      team.setName("Product team");
      teams.save(team);
      for (int i = 0; i < 4; i++) {
        var m = new TeamMember();
        m.setTeam(team);
        m.setUser(people.get(i));
        teamMembers.save(m);
      }
      for (int i = 0; i < titles[pi].length; i++) {
        var t = new Task();
        t.setProject(p);
        t.setCreator(people.get(2));
        t.setTitle(titles[pi][i]);
        t.setDescription(
            "Agree on the scope with the product team, document the decisions, and share the outcome for review.\n\nAcceptance criteria\n- The proposed approach is documented.\n- Accessibility and responsive behavior are considered.\n- The team has reviewed the outcome.");
        t.setAssignee(i == 12 ? null : (i % 3 == 0 ? people.get(1) : people.get(0)));
        t.setTeam(team);
        t.setStatus(
            i % 5 == 0
                ? TaskStatus.COMPLETED
                : i % 3 == 0 ? TaskStatus.IN_PROGRESS : TaskStatus.TODO);
        t.setPriority(Priority.values()[i % 4]);
        t.setDueDate(today.plusDays(i - 2));
        t.setCreatedAt(Instant.now().minus(Duration.ofDays(10)));
        if (t.getStatus() == TaskStatus.COMPLETED)
          t.setCompletedAt(Instant.now().minus(Duration.ofDays(1)));
        tasks.save(t);
        var log = new ActivityLog();
        log.setProjectId(p.getId());
        log.setTaskId(t.getId());
        log.setActorId(people.get(2).getId());
        log.setActorName(people.get(2).getFullName());
        log.setAction("TASK_CREATED");
        log.setDescription("Created " + t.getTitle());
        logs.save(log);
        if (i < 2) {
          var n = new Notification();
          n.setRecipient(people.get(0));
          n.setProjectId(p.getId());
          n.setTaskId(t.getId());
          n.setTitle("Ready for your review");
          n.setMessage(t.getTitle());
          notifications.save(n);
        }
      }
    }
  }
}
