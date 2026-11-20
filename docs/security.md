# Security model and operating limits

## Identity and sessions

Registration grants only the USER system role. Project ownership comes from creating a project, not from a system-role input. Passwords use BCrypt with cost 12; passwords longer than 72 UTF-8 bytes are rejected instead of silently truncated.

Access JWTs expire after ten minutes and have issuer, audience, algorithm and account-version validation. Deactivated accounts and invalidated password sessions are rejected on every authenticated request. Access tokens are kept in memory. Refresh tokens have 256 bits of entropy, are hashed with SHA-256 in PostgreSQL, expire after 14 days, and rotate after every refresh. Replaying a consumed token revokes its entire family. Logout revokes that family. Logout does not revoke an already issued access token; it expires within ten minutes. Password changes and account deactivation invalidate access immediately.

Refresh cookies are HttpOnly, SameSite=Strict, scoped to `/api/v1/auth`, and Secure when `COOKIE_SECURE=true`. Cookie-authenticated endpoints reject untrusted Origin headers and cross-site Fetch Metadata. CORS uses an explicit allowlist. Authentication throttling is bounded and per-IP, in memory. Deploy behind a gateway with a shared rate limit before running multiple API replicas. Do not trust arbitrary forwarded client-IP headers.

Password reset responses are generic. A reset link expires in 15 minutes, works once, and revokes prior sessions. Email is dispatched asynchronously only after the reset-token transaction commits, so SMTP errors do not alter the response based on whether an account exists. The bounded in-memory queue is best effort: failures are logged without tokens, and a restart can lose queued mail. Local SMTP uses Mailpit. Production needs a real TLS-enabled SMTP provider, a durable outbox and delivery monitoring.

The browser shares in-flight refresh requests and uses Web Locks to serialize refresh-cookie rotation across tabs where supported. Tokens never enter localStorage. Older browsers without Web Locks have only per-tab serialization.

## Authorization

`PermissionService` owns the policy. Access always requires project membership or the ADMIN system role. System administrators have an explicit project-oversight override. Managers cannot appoint other managers, modify the owner, or change peer managers. Ownership transfer demotes the former owner and promotes exactly one active project member under a project lock.

Team leads may create and mutate tasks only in teams they belong to. Assignment also requires the target to be active, a non-viewer project member, and a member of the task's team when it has one. Members can edit assigned tasks, self-assign under the project policy, and request reassignment. Assigning someone else is disabled for members unless the project policy explicitly enables it. Viewers are read-only.

Project report access is limited to owners, managers, administrators and team leads. Team-lead reports contain only their own team's tasks. Project lists, task lists, comments, labels, attachments and dependency lookups all enforce access at the service boundary.

Administrator changes serialize on administrator rows and recheck the acting administrator after acquiring the lock. Administrators cannot deactivate or demote themselves. Project-role lookups may be cached within one GET request only; authorization is never cached across requests or mutations.

## Attachments

Task attachments require project read access to list/download and comment permission to upload. Uploaders or authorized task managers may delete files while the project is writable. Viewers cannot upload or delete. Files are limited to 10 MB and 50 per task. The server ignores client MIME claims, validates supported extensions and file signatures/UTF-8 text, checks image dimensions without decoding full pixel buffers, rejects path separators/control characters, and generates independent UUID storage keys.

Downloads use `application/octet-stream`, `Content-Disposition: attachment`, `nosniff` and no-store caching. Content checks are not malware scanning; PDFs may contain active content and CSVs may contain formulas when opened in desktop software. Treat downloaded files as untrusted.

File writes are removed on transaction rollback. Deletes run only after database commit, including task/project cascade deletion. A process crash or filesystem failure can leave orphan files; monitor cleanup warnings and reconcile metadata against storage during maintenance. Back up both PostgreSQL and the upload volume.

## Data integrity

- DTO request types are allowlists; entity properties and privileged role fields cannot be mass-assigned.
- Task and project updates carry an optimistic version; stale writes return 409.
- Dependency graph mutations serialize on the project row. Recursive SQL detects cycles.
- Recurring instances are protected by a row lock and a unique `(recurrence_id, occurrence_date)` constraint. Monthly schedules retain their original day across shorter months. Each scheduler pass catches up at most 31 occurrences per rule.
- Archived projects reject task and collaboration writes. A project must be archived before deletion. Deletion is explicit, with child records cascaded by PostgreSQL and audit records retained independently.
- Flyway migrations are immutable after use. Hibernate never creates or updates the production schema.

## Before exposing a deployment publicly

Use HTTPS, secure cookies, exact allowed origins, new random secrets, managed backups for PostgreSQL and uploaded files, production SMTP, disk quotas and monitoring. Disable development seeding. Restrict Swagger and actuator access at the gateway if the deployment requires private documentation. Local attachment storage and per-process throttling are deliberate first-deployment choices, not a multi-region architecture. File scanning and a durable mail delivery queue are not included.
