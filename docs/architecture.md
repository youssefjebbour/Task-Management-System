# TaskFlow architecture

TaskFlow is a modular monolith: React and TypeScript call a versioned Spring Boot API, which uses a service layer, JPA repositories and PostgreSQL. Flyway owns the schema; Hibernate only validates it.

## Decisions

- Java 21 is project-local during development. Existing global Java installations and unrelated Docker services stay intact.
- Authentication uses short-lived, signed access JWTs held in browser memory. Opaque refresh tokens are hashed at rest, rotated on use and carried only in an HttpOnly SameSite=Strict cookie. Origin validation protects cookie-authenticated mutations. Deactivation and password changes invalidate existing JWTs via an account token version.
- Project roles are separate from system roles. `PermissionService` is the authority for project access and task operations. DTO capabilities reflect these rules for the UI, but every write checks them again on the server.
- Projects own teams; a team lead's task and assignment scope is the task's team. Members may edit their assigned tasks, self-assign unassigned tasks when allowed, and never assign other people unless a project policy explicitly permits it.
- DTOs are explicit allowlists. Entities never cross the API boundary. Full updates carry a version to reject stale edits; assignments and status changes have dedicated endpoints.
- Successes return resource DTOs, lists return a consistent pagination envelope, and failures return `{status,message,errors,timestamp}`. Dates are ISO 8601; due dates are date-only in the configured workspace timezone.
- Reports aggregate persisted task records. Development seed data is opt-in and labeled; fresh registered users start with an empty workspace.
- Password recovery sends a single-use, expiring link through SMTP (Mailpit locally). Reset tokens never appear in API responses or logs.
- The reference image supplies the visual direction: pale neutral navigation, white working surface, blue underline tabs, fine row rules, muted semantic badges and compact controls. A connected-flow mark and task state indicators give TaskFlow its identity. Every screen uses these tokens in light and dark themes.
- Attachments use a deliberately small local-storage boundary. Metadata is transactional PostgreSQL data; file writes register rollback cleanup and physical deletes run after commit. Project locking coordinates uploads with project/task deletion. UUID storage keys and download disposition separate storage paths from user filenames. Crash reconciliation remains an operational responsibility.
- Role lookups are cached only within read requests. Indexed predicates and count queries power lists/reports; report aggregation does not load all task entities. Per-member counts are appropriate for moderate workspaces and are a future candidate for grouped SQL at larger scale.
- “Due this week” means open tasks due Monday through Sunday in the configured workspace timezone, including overdue dates within that week. “On your radar” excludes completed/cancelled tasks.
- Password-reset email is an after-commit event handled by a bounded executor. This keeps SMTP failures out of the account-discovery response path; a durable outbox is intentionally deferred and documented.

## Screen questions

My Tasks: what needs my attention next? Overview: where is my work blocked or overdue? Projects: which workstreams can I access? Board: how is work moving? Members and Team: who can work on this project? Reports: where is the remaining work concentrated? Notifications: what changed? Calendar: what is due each day? Settings: what policy or profile detail needs changing?

## Verification gates

Each phase must pass the Maven build and relevant tests, the TypeScript/Vite build, real PostgreSQL migrations, API smoke checks and browser inspection. The work log records actual results and limitations.
