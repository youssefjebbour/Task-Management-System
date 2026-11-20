import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, post } from '../lib/api';
import type { Member, Page, Project, Task, Team } from '../lib/types';
import { useAuth } from '../lib/auth';
import { priorityLabels } from '../lib/format';
import { Button, FormError, Modal, useToast } from './ui';
export function ProjectCreate({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate(),
    client = useQueryClient(),
    toast = useToast();
  const [key, setKey] = useState('');
  const mutation = useMutation({
    mutationFn: (values: unknown) => post<Project>('/projects', values),
    onSuccess: (project) => {
      client.invalidateQueries({ queryKey: ['projects'] });
      onClose();
      toast('Project created');
      navigate(`/projects/${project.id}/overview`);
    },
  });
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    mutation.mutate({ ...f, startDate: f.startDate || null, deadline: f.deadline || null });
  }
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Create a project"
      description="Give your team a shared place to move work forward."
    >
      <form className="form-stack" onSubmit={submit}>
        <label>
          Project name
          <input name="name" required maxLength={120} placeholder="e.g. Customer onboarding" />
        </label>
        <label>
          Project key
          <input
            name="projectKey"
            required
            minLength={2}
            maxLength={10}
            pattern="[A-Za-z][A-Za-z0-9]{1,9}"
            value={key}
            onChange={(e) => setKey(e.target.value.toUpperCase())}
            placeholder="e.g. ONB"
          />
          <small>A short, unique identifier for this project.</small>
        </label>
        <label>
          Description
          <textarea
            name="description"
            maxLength={10000}
            rows={3}
            placeholder="What are we working toward?"
          />
        </label>
        <div className="form-grid">
          <label>
            Start date
            <input type="date" name="startDate" />
          </label>
          <label>
            Deadline
            <input type="date" name="deadline" />
          </label>
        </div>
        <FormError error={mutation.error} />
        <div className="form-actions">
          <Button type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" busy={mutation.isPending}>
            Create project
          </Button>
        </div>
      </form>
    </Modal>
  );
}
export function TaskCreate({
  open,
  onClose,
  projectId,
}: {
  open: boolean;
  onClose: () => void;
  projectId?: string;
}) {
  const { user } = useAuth(),
    client = useQueryClient(),
    toast = useToast();
  const [selected, setSelected] = useState(projectId ?? '');
  const projects = useQuery({
    queryKey: ['projects', 'navigation'],
    queryFn: () => api<Page<Project>>('/projects?size=100'),
  });
  const available = projects.data?.content.filter((p) => p.canCreateTask) ?? [];
  const chosen = selected || available[0]?.id || '';
  const project = available.find((p) => p.id === chosen);
  const members = useQuery({
    queryKey: ['members', chosen],
    queryFn: () => api<Member[]>(`/projects/${chosen}/members`),
    enabled: !!chosen,
  });
  const teams = useQuery({
    queryKey: ['teams', chosen],
    queryFn: () => api<Team[]>(`/teams?projectId=${chosen}`),
    enabled: !!chosen,
    retry: false,
  });
  const mutation = useMutation({
    mutationFn: (body: unknown) => post<Task>('/tasks', body),
    onSuccess: () => {
      client.invalidateQueries();
      onClose();
      toast('Task created');
    },
  });
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    mutation.mutate({
      ...f,
      projectId: chosen,
      dueDate: f.dueDate || null,
      assigneeId: f.assigneeId || null,
      teamId: f.teamId || null,
    });
  }
  const canAssign =
    project?.canManage || project?.role === 'TEAM_LEAD' || project?.membersCanAssign;
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Create a task"
      description="A clear next step, with the right person behind it."
    >
      <form className="form-stack" onSubmit={submit}>
        <label>
          Project
          <select value={chosen} onChange={(e) => setSelected(e.target.value)} required>
            {available.length === 0 && <option value="">Create a project first</option>}
            {available.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Task title
          <input
            name="title"
            required
            maxLength={200}
            placeholder="What needs to get done?"
            autoFocus
          />
        </label>
        <label>
          Description
          <textarea
            name="description"
            rows={4}
            maxLength={20000}
            placeholder="Add context, a plan, or acceptance criteria…"
          />
        </label>
        <div className="form-grid">
          <label>
            Priority
            <select name="priority" defaultValue="MEDIUM">
              {Object.entries(priorityLabels).map(([v, n]) => (
                <option key={v} value={v}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <label>
            Due date
            <input name="dueDate" type="date" />
          </label>
          <label>
            Assignee
            <select name="assigneeId" defaultValue="">
              <option value="">Unassigned</option>
              {members.data
                ?.filter(
                  (m) =>
                    m.user.active &&
                    m.role !== 'VIEWER' &&
                    (canAssign || (m.user.id === user?.id && project?.membersCanSelfAssign)),
                )
                .map((m) => (
                  <option key={m.user.id} value={m.user.id}>
                    {m.user.fullName}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Team
            <select name="teamId" defaultValue="">
              <option value="">No team</option>
              {teams.data?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <FormError error={mutation.error} />
        <div className="form-actions">
          <Button type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" disabled={!chosen} busy={mutation.isPending}>
            Create task
          </Button>
        </div>
      </form>
    </Modal>
  );
}
