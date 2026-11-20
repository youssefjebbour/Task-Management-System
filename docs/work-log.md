# Implementation work log

## Inspection

- Existing content: `insp/inspiration.png` only. No Git repository or source files.
- Active Java: 17.0.9; Maven: 3.9.9; Node: 24.21.0; npm: 11.19.0; Docker engine: 29.9.3; Compose: 5.7.0.
- Existing unrelated PostgreSQL and Redis containers use ports 5435 and 6382. TaskFlow reserves 5436, 8080, 5173 and local mail ports.
- Reference reviewed. App style: framed sheet, seed 1280114459; supplied reference takes precedence over random visual choices.

## Phases

1. Foundation, authentication, project permissions, task workflows and integrated UI — verified.
2. Collaboration, teams, dependencies, recurrence and reports — verified.
3. Attachments, security tests, Docker, CI and documentation — verified.
4. Browser verification, accessibility, responsive and theme refinement — verified.

## Phase 1 verification

- Java 21.0.13 downloaded from Adoptium with SHA-256 verification, scoped to `.tools`.
- Backend package builds successfully. Eight JUnit/Mockito/Testcontainers tests pass with no skips, using a disposable PostgreSQL 17 container.
- Flyway V1 applied and Hibernate validation passed on both local and test databases.
- TypeScript and Vite production build pass. npm reports no known vulnerabilities for the installed dependencies.
- Browser smoke test passes: real login, persisted records, details drawer, report aggregation, refresh-cookie restoration, and no horizontal page overflow at 1440/768/390 widths.
- Inspected screenshots for the task list at all three widths and the drawer/dashboard. Removed offscreen mobile-sidebar shadow and increased small labels to 11px.
- Fixed the local Maven HTTP mirror with project-only settings. Fixed a Windows JAR file lock by running a copy in `.runtime` so Maven can rebuild its output.
- Remaining phase-specific work: collaboration screens and features, broader security/business tests, deployment files, final design inspection.

## Phase 2 verification

- Added comments, labels, dependency cycle checks/completion guards, teams, persistent notifications/activity, reports and recurring task generation.
- Backend package passed 20 tests; TypeScript/Vite build passed. Flyway V2 applied to local and Testcontainers databases.
- Three browser workflows passed against the real services, including project/task/comment/label/board persistence, all primary routes, both themes, responsive navigation and admin screens.
- Fixed project graph deletion to use a transactionally flushed native PostgreSQL cascade, with an integration regression test.

## Phase 3 verification

- Added attachment metadata migration V3, validated multipart uploads, confined UUID file storage, permission-checked downloads/deletions, and drawer controls. Actual multipart tests verify accepted text, forbidden viewer/outsider operations, spoofed/empty/oversized files, and cleanup after attachment/task/project deletion.
- Corrected an attachment foreign-key name found by Flyway tests. Configured bounded Tomcat request swallowing so rejected 10 MB uploads return the intended HTTP 413 instead of a connection reset.
- Added asynchronous post-commit password-reset mail, cross-tab refresh coordination, read-request role caching, administrator mutation locking and explicit disconnected-client handling. Documentation states the limits of in-memory rate limits, file validation and best-effort mail.
- Corrected calendar-week report boundaries and added a reconciliation regression test. The final Maven verify run passes 25 tests with no failures/errors/skips; all three migrations validate in Testcontainers and the retained local database.
- Added multi-stage API/web Dockerfiles, Nginx same-origin API proxy and security headers, named upload storage, health checks and Compose startup. All four services started healthy. The API runs as an unprivileged container user.
- Added setup/stop/restart helpers, README, security/architecture notes, Prettier/Spotless formatting and GitHub Actions jobs. Frontend API-client unit tests pass (5 tests); production builds and formatting checks pass.
- Removed only the confirmed archived fixture left by an earlier browser-test failure after matching its exact title, description and sole test task. Existing seed/user data and unrelated Docker services were preserved.
- npm audit reports zero known vulnerabilities. Generated secrets were checked against source/tests/scripts/docs/CI without printing their values; no matches found. `.env`, `.tools`, `.runtime`, dependency folders and build outputs remain ignored.

## Phase 4 verification

- Captured all requested auth/workspace/project/admin screens, plus phone/tablet layouts and dark theme, using real database records; no browser errors or horizontal page overflow.
- Refined small working-data labels, removed a resting navigation shadow, corrected footer contrast/margins, fixed profile accessible naming and an over-broad CSS selector that reduced avatar contrast. Theme radios now support arrow/Home/End keys.
- Final My Tasks inspector: zero FAIL findings at 1440/768/390. The phone retains a small-text warning for secondary metadata and avatar initials; working titles and data remain 13–14px.
- Real Docker browser workflows cover registration/empty workspace, SMTP recovery, task/comment/label/attachment persistence, actual pointer drag/drop, viewer restrictions, simultaneous tab refresh, themes, navigation and admin routes. Automated accessibility checks supplement keyboard and visual checks; they are not a complete assistive-technology audit.
- Final run: all six Playwright tests pass against the production Nginx/Java/PostgreSQL/Mailpit Compose stack. Axe reports no WCAG A/AA violations on the tested login, task list, overview, settings, dark task list and phone task list states. Theme radio keyboard interaction and drawer Escape behavior pass.
- Final frontend formatting check, five unit tests and TypeScript/Vite production build pass. The API, database, web and mail containers remain running and healthy at the documented localhost ports.
- Resolved the last Java formatting failure after adding `.gitattributes`: all 88 Java files now use LF line endings. A before/after comparison confirmed no changes beyond line endings, and `spotless:check` passes.
- The GitHub Actions configuration is present; no remote run, push or public deployment was performed. Remaining deployment boundaries are documented in `security.md` and the README.
