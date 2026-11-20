CREATE TABLE comments (
 id UUID PRIMARY KEY, task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
 author_id UUID NOT NULL REFERENCES app_users(id), body TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX comments_task_idx ON comments(task_id,created_at);
CREATE TABLE labels (
 id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 name VARCHAR(50) NOT NULL, color VARCHAR(7) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(project_id,name)
);
CREATE TABLE task_labels (
 id UUID PRIMARY KEY, task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
 label_id UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(task_id,label_id)
);
CREATE INDEX task_labels_label_idx ON task_labels(label_id);
CREATE TABLE task_dependencies (
 id UUID PRIMARY KEY, task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
 blocker_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(task_id,blocker_id), CHECK(task_id <> blocker_id)
);
CREATE INDEX dependencies_blocker_idx ON task_dependencies(blocker_id);
CREATE TABLE recurrence_rules (
 id UUID PRIMARY KEY, template_id UUID NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE CASCADE,
 frequency VARCHAR(12) NOT NULL CHECK(frequency IN ('DAILY','WEEKLY','MONTHLY')),
 anchor_day INTEGER NOT NULL CHECK(anchor_day BETWEEN 1 AND 31),
 next_date DATE NOT NULL, active BOOLEAN NOT NULL DEFAULT true,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE tasks ADD CONSTRAINT task_recurrence_fk FOREIGN KEY(recurrence_id) REFERENCES recurrence_rules(id) ON DELETE SET NULL;
CREATE INDEX recurrence_due_idx ON recurrence_rules(next_date) WHERE active;
