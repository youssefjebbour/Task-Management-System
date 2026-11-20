CREATE TABLE app_users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, username VARCHAR(40) NOT NULL UNIQUE,
 full_name VARCHAR(100) NOT NULL, password_hash VARCHAR(255) NOT NULL, system_role VARCHAR(16) NOT NULL CHECK (system_role IN ('ADMIN','USER')),
 active BOOLEAN NOT NULL DEFAULT true, job_title VARCHAR(120), token_version INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE projects (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, project_key VARCHAR(10) NOT NULL UNIQUE, description TEXT,
 status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNING','ACTIVE','ON_HOLD','COMPLETED','ARCHIVED')),
 owner_id UUID NOT NULL REFERENCES app_users(id), start_date DATE, deadline DATE,
 members_can_create BOOLEAN NOT NULL DEFAULT true, members_can_self_assign BOOLEAN NOT NULL DEFAULT true,
 members_can_assign BOOLEAN NOT NULL DEFAULT false, block_incomplete_dependencies BOOLEAN NOT NULL DEFAULT true,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT project_dates CHECK (deadline IS NULL OR start_date IS NULL OR deadline >= start_date)
);
CREATE TABLE project_members (
 id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES app_users(id), role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER','MANAGER','TEAM_LEAD','MEMBER','VIEWER')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(project_id,user_id)
);
CREATE UNIQUE INDEX one_owner_per_project ON project_members(project_id) WHERE role = 'OWNER';
CREATE INDEX project_members_user_idx ON project_members(user_id);
CREATE TABLE teams (
 id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE, name VARCHAR(100) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0, UNIQUE(project_id,name)
);
CREATE TABLE team_members (
 id UUID PRIMARY KEY, team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES app_users(id), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, UNIQUE(team_id,user_id)
);
CREATE INDEX team_members_user_idx ON team_members(user_id);
CREATE TABLE tasks (
 id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 creator_id UUID NOT NULL REFERENCES app_users(id), assignee_id UUID REFERENCES app_users(id), team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
 title VARCHAR(200) NOT NULL, description TEXT, status VARCHAR(20) NOT NULL CHECK (status IN ('TODO','IN_PROGRESS','COMPLETED','CANCELLED')),
 priority VARCHAR(12) NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')), due_date DATE, completed_at TIMESTAMPTZ,
 recurrence_id UUID, occurrence_date DATE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(recurrence_id,occurrence_date)
);
CREATE INDEX tasks_project_status_idx ON tasks(project_id,status);
CREATE INDEX tasks_assignee_due_idx ON tasks(assignee_id,due_date);
CREATE INDEX tasks_team_idx ON tasks(team_id);
CREATE INDEX tasks_creator_idx ON tasks(creator_id);
CREATE INDEX tasks_due_open_idx ON tasks(due_date) WHERE status IN ('TODO','IN_PROGRESS');
CREATE TABLE refresh_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), token_hash VARCHAR(64) NOT NULL UNIQUE,
 family_id UUID NOT NULL, expires_at TIMESTAMPTZ NOT NULL, consumed_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX refresh_family_idx ON refresh_tokens(family_id);
CREATE INDEX refresh_user_idx ON refresh_tokens(user_id);
CREATE TABLE password_reset_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), token_hash VARCHAR(64) NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE activity_logs (
 id UUID PRIMARY KEY, project_id UUID, task_id UUID, actor_id UUID, actor_name VARCHAR(100) NOT NULL,
 action VARCHAR(50) NOT NULL, description TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX activity_project_time_idx ON activity_logs(project_id,created_at DESC);
CREATE INDEX activity_task_time_idx ON activity_logs(task_id,created_at DESC);
CREATE TABLE notifications (
 id UUID PRIMARY KEY, recipient_id UUID NOT NULL REFERENCES app_users(id), project_id UUID, task_id UUID,
 title VARCHAR(160) NOT NULL, message TEXT NOT NULL, read_at TIMESTAMPTZ, dedup_key VARCHAR(200) UNIQUE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX notifications_recipient_idx ON notifications(recipient_id,created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications(recipient_id) WHERE read_at IS NULL;
