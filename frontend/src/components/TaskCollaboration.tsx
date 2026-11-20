import { useState, type FormEvent } from 'react';
import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowUpRight, Plus, X, MessageSquare, Repeat2, Link2 } from 'lucide-react';
import { api, post, put, del } from '../lib/api';
import type { Task, Page, Comment, Label, Dependency, Recurrence, Project } from '../lib/types';
import { dateInput, relative, statusLabels } from '../lib/format';
import {
  Avatar,
  Button,
  Confirm,
  Empty,
  ErrorState,
  FormError,
  Loading,
  Modal,
  useToast,
} from './ui';
import { useAppUI } from './AppLayout';
import { TaskAttachments } from './TaskAttachments';

export function TaskCollaboration({ task }: { task: Task }) {
  const project = useQuery({
    queryKey: ['project', task.projectId],
    queryFn: () => api<Project>(`/projects/${task.projectId}`),
  });
  return (
    <>
      <TaskLabels task={task} manage={!!project.data?.canManage} />
      <Dependencies task={task} />
      {project.data?.canManage && <Recurring task={task} />}
      <TaskAttachments task={task} />
      <Comments task={task} />
    </>
  );
}
function TaskLabels({ task, manage }: { task: Task; manage: boolean }) {
  const client = useQueryClient(),
    toast = useToast();
  const [create, setCreate] = useState(false);
  const labels = useQuery({
    queryKey: ['labels', task.projectId],
    queryFn: () => api<Label[]>(`/projects/${task.projectId}/labels`),
  });
  const assigned = useQuery({
    queryKey: ['task-labels', task.id],
    queryFn: () => api<Label[]>(`/tasks/${task.id}/labels`),
  });
  const change = useMutation({
    mutationFn: ({ id, remove }: { id: string; remove?: boolean }) =>
      remove ? del(`/tasks/${task.id}/labels/${id}`) : post(`/tasks/${task.id}/labels`, { id }),
    onSuccess: () => client.invalidateQueries(),
    onError: (e) => toast(e.message, 'error'),
  });
  const add = useMutation({
    mutationFn: (values: unknown) => post<Label>(`/projects/${task.projectId}/labels`, values),
    onSuccess: (label) => {
      client.invalidateQueries({ queryKey: ['labels'] });
      setCreate(false);
      if (task.permissions.edit) change.mutate({ id: label.id });
      toast('Label created');
    },
  });
  return (
    <section className="drawer-section">
      <div className="section-title">
        <h3>Labels</h3>
        {manage && (
          <button className="text-action" onClick={() => setCreate(true)}>
            Create label
          </button>
        )}
      </div>
      {assigned.isError ? (
        <ErrorState error={assigned.error} retry={() => assigned.refetch()} />
      ) : (
        <div className="task-labels">
          {assigned.data?.map((l) => (
            <span className="task-label" key={l.id}>
              <i style={{ background: l.color }} />
              {l.name}
              {task.permissions.edit && (
                <button
                  aria-label={`Remove ${l.name} label`}
                  onClick={() => change.mutate({ id: l.id, remove: true })}
                >
                  <X size={12} />
                </button>
              )}
            </span>
          ))}
          {assigned.data?.length === 0 && <span className="muted">No labels</span>}
          {task.permissions.edit && (
            <select
              aria-label="Add label"
              value=""
              disabled={change.isPending}
              onChange={(e) => change.mutate({ id: e.target.value })}
            >
              <option value="">+ Add label</option>
              {labels.data
                ?.filter((l) => !assigned.data?.some((a) => a.id === l.id))
                .map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
            </select>
          )}
        </div>
      )}
      <Modal open={create} onClose={() => setCreate(false)} title="Create a project label">
        <form
          className="form-stack"
          onSubmit={(e) => {
            e.preventDefault();
            add.mutate(Object.fromEntries(new FormData(e.currentTarget)));
          }}
        >
          <label>
            Label name
            <input name="name" required maxLength={50} placeholder="e.g. Accessibility" />
          </label>
          <label>
            Color
            <input name="color" type="color" defaultValue="#2563b8" />
          </label>
          <FormError error={add.error} />
          <div className="form-actions">
            <Button variant="primary" busy={add.isPending}>
              Create label
            </Button>
          </div>
        </form>
      </Modal>
    </section>
  );
}
function Dependencies({ task }: { task: Task }) {
  const { openTask } = useAppUI();
  const client = useQueryClient(),
    toast = useToast();
  const [adding, setAdding] = useState(false),
    [q, setQ] = useState('');
  const query = useQuery({
    queryKey: ['dependencies', task.id],
    queryFn: () =>
      api<{ blockedBy: Dependency[]; blocking: Dependency[] }>(`/tasks/${task.id}/dependencies`),
  });
  const options = useQuery({
    queryKey: ['tasks', 'dependency-options', task.projectId, q],
    queryFn: () =>
      api<Page<Task>>(
        `/tasks?scope=all&projectId=${task.projectId}&q=${encodeURIComponent(q)}&size=20`,
      ),
    enabled: adding,
  });
  const change = useMutation({
    mutationFn: ({ id, remove }: { id: string; remove?: boolean }) =>
      remove ? del(`/dependencies/${id}`) : post(`/tasks/${task.id}/dependencies`, { id }),
    onSuccess: () => {
      client.invalidateQueries();
      setAdding(false);
      toast('Dependencies updated');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  return (
    <section className="drawer-section">
      <div className="section-title">
        <h3>Dependencies</h3>
        {task.permissions.edit && (
          <button className="text-action" onClick={() => setAdding(!adding)}>
            <Plus size={13} />
            Add blocker
          </button>
        )}
      </div>
      {query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : (
        <>
          {query.data?.blockedBy.length === 0 && query.data?.blocking.length === 0 && (
            <p className="muted">No dependencies. This task can move independently.</p>
          )}
          {query.data?.blockedBy.map((d) => (
            <div className="dependency-row" key={d.id}>
              <Link2 size={14} />
              <button onClick={() => openTask(d.taskId)}>
                <span>Blocked by · {d.key}</span>
                <strong>{d.title}</strong>
              </button>
              <span
                className={`status-dot dot-${d.status.toLowerCase()}`}
                title={statusLabels[d.status]}
              />
              {task.permissions.edit && (
                <button
                  className="icon-button"
                  aria-label={`Remove blocker ${d.title}`}
                  onClick={() => change.mutate({ id: d.id, remove: true })}
                >
                  <X size={13} />
                </button>
              )}
            </div>
          ))}
          {query.data?.blocking.map((d) => (
            <div className="dependency-row" key={d.id}>
              <Link2 size={14} />
              <button onClick={() => openTask(d.taskId)}>
                <span>Blocking · {d.key}</span>
                <strong>{d.title}</strong>
              </button>
              <ArrowUpRight size={14} />
            </div>
          ))}
        </>
      )}
      {adding && (
        <div className="dependency-search">
          <input
            aria-label="Find a blocking task"
            placeholder="Search project tasks…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          {options.isPending ? (
            <Loading rows={2} />
          ) : options.isError ? (
            <ErrorState error={options.error} retry={() => options.refetch()} />
          ) : (
            options.data.content
              .filter(
                (t) => t.id !== task.id && !query.data?.blockedBy.some((d) => d.taskId === t.id),
              )
              .map((t) => (
                <button
                  key={t.id}
                  disabled={change.isPending}
                  onClick={() => change.mutate({ id: t.id })}
                >
                  <span>{t.key}</span>
                  {t.title}
                  <Plus size={13} />
                </button>
              ))
          )}
        </div>
      )}
    </section>
  );
}
function Recurring({ task }: { task: Task }) {
  const client = useQueryClient(),
    toast = useToast();
  const [editing, setEditing] = useState(false);
  const query = useQuery({
    queryKey: ['recurrence', task.id],
    queryFn: () => api<Partial<Recurrence>>(`/tasks/${task.id}/recurrence`),
  });
  const change = useMutation({
    mutationFn: (body: unknown) => put(`/tasks/${task.id}/recurrence`, body),
    onSuccess: () => {
      client.invalidateQueries();
      setEditing(false);
      toast('Recurring schedule saved');
    },
  });
  const today = dateInput(new Date());
  return (
    <section className="drawer-section">
      <div className="section-title">
        <h3>Recurring schedule</h3>
        <button className="text-action" onClick={() => setEditing(!editing)}>
          {query.data?.id ? 'Manage' : 'Set up'}
        </button>
      </div>
      {query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : editing ? (
        <form
          className="form-stack"
          onSubmit={(e) => {
            e.preventDefault();
            const data = new FormData(e.currentTarget);
            change.mutate({ ...Object.fromEntries(data), active: data.has('active') });
          }}
        >
          <div className="form-grid">
            <label>
              Repeat
              <select name="frequency" defaultValue={query.data?.frequency || 'WEEKLY'}>
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
                <option value="MONTHLY">Monthly</option>
              </select>
            </label>
            <label>
              Next occurrence
              <input
                type="date"
                name="nextDate"
                required
                min={today}
                defaultValue={
                  query.data?.nextDate && query.data.nextDate > today ? query.data.nextDate : today
                }
              />
            </label>
          </div>
          <label className="checkbox-label">
            <input name="active" type="checkbox" defaultChecked={query.data?.active ?? true} />
            Schedule enabled
          </label>
          <p className="muted">Creates a new task on each date, using this task as the template.</p>
          <FormError error={change.error} />
          <div className="form-actions">
            <Button type="button" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button busy={change.isPending}>Save schedule</Button>
          </div>
        </form>
      ) : (
        <p className="recurrence-summary">
          <Repeat2 size={15} />
          {query.data?.id
            ? `${query.data.frequency?.toLowerCase()} · ${query.data.active ? 'Next: ' + query.data.nextDate : 'Paused'}`
            : 'Does not repeat'}
        </p>
      )}
    </section>
  );
}
function Comments({ task }: { task: Task }) {
  const client = useQueryClient(),
    toast = useToast();
  const [body, setBody] = useState(''),
    [editing, setEditing] = useState<Comment | null>(null),
    [removing, setRemoving] = useState<Comment | null>(null);
  const query = useInfiniteQuery({
    queryKey: ['comments', task.id],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api<Page<Comment>>(`/tasks/${task.id}/comments?page=${pageParam}&size=20`),
    getNextPageParam: (p) => (p.page + 1 < p.totalPages ? p.page + 1 : undefined),
  });
  const add = useMutation({
    mutationFn: () => post(`/tasks/${task.id}/comments`, { body }),
    onSuccess: () => {
      client.invalidateQueries();
      setBody('');
      toast('Comment added');
    },
  });
  const edit = useMutation({
    mutationFn: (body: string) => put(`/comments/${editing!.id}`, { body }),
    onSuccess: () => {
      client.invalidateQueries();
      setEditing(null);
      toast('Comment updated');
    },
  });
  const remove = useMutation({
    mutationFn: () => del(`/comments/${removing!.id}`),
    onSuccess: () => {
      client.invalidateQueries();
      setRemoving(null);
      toast('Comment removed');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  return (
    <section className="drawer-section">
      <div className="section-title">
        <h3>Comments</h3>
        <span>{query.data?.pages[0].totalElements ?? 0}</span>
      </div>
      {task.permissions.comment && (
        <form
          className="comment-composer"
          onSubmit={(e) => {
            e.preventDefault();
            add.mutate();
          }}
        >
          <label className="sr-only" htmlFor="new-comment">
            Add a comment
          </label>
          <textarea
            id="new-comment"
            required
            maxLength={10000}
            rows={3}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Share an update or ask a question…"
          />
          <FormError error={add.error} />
          <div className="form-actions">
            <Button busy={add.isPending} disabled={!body.trim()}>
              <MessageSquare size={14} />
              Comment
            </Button>
          </div>
        </form>
      )}
      {query.isPending ? (
        <Loading rows={2} />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : query.data.pages[0].totalElements === 0 ? (
        <p className="comment-empty">No comments yet. Keep the conversation close to the work.</p>
      ) : (
        <div className="comments-list">
          {query.data.pages
            .flatMap((p) => p.content)
            .map((c) => (
              <article className="comment" key={c.id}>
                <Avatar person={c.author} />
                <div className="comment-main">
                  <div className="comment-meta">
                    <strong>{c.author.fullName}</strong>
                    <span>
                      {relative(c.createdAt)}
                      {c.updatedAt !== c.createdAt ? ' · edited' : ''}
                    </span>
                  </div>
                  <p>{c.body}</p>
                  <div className="comment-actions">
                    {c.canEdit && <button onClick={() => setEditing(c)}>Edit</button>}
                    {c.canDelete && <button onClick={() => setRemoving(c)}>Delete</button>}
                  </div>
                </div>
              </article>
            ))}
        </div>
      )}
      {query.hasNextPage && (
        <Button
          variant="ghost"
          onClick={() => query.fetchNextPage()}
          busy={query.isFetchingNextPage}
        >
          Load more comments
        </Button>
      )}
      <Modal open={!!editing} onClose={() => setEditing(null)} title="Edit comment">
        <form
          className="form-stack"
          key={editing?.id}
          onSubmit={(e: FormEvent<HTMLFormElement>) => {
            e.preventDefault();
            edit.mutate(String(new FormData(e.currentTarget).get('body')));
          }}
        >
          <label>
            Comment
            <textarea
              name="body"
              defaultValue={editing?.body}
              required
              maxLength={10000}
              rows={5}
            />
          </label>
          <FormError error={edit.error} />
          <div className="form-actions">
            <Button variant="primary" busy={edit.isPending}>
              Save comment
            </Button>
          </div>
        </form>
      </Modal>
      <Confirm
        open={!!removing}
        onClose={() => setRemoving(null)}
        title="Delete this comment?"
        description="The comment will be permanently removed from this task."
        onConfirm={() => remove.mutate()}
        busy={remove.isPending}
      />
    </section>
  );
}
