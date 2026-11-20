import { useState, type FormEvent } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { X, Trash2, ArrowUpRight, Clock3, UserRound } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api, put, patch, del, post } from '../lib/api';
import { useAuth } from '../lib/auth';
import type { Activity, Page, Task, Member, Team } from '../lib/types';
import { date, relative, statusLabels, priorityLabels } from '../lib/format';
import {
  Avatar,
  Button,
  Confirm,
  ErrorState,
  FormError,
  Loading,
  StatusBadge,
  useToast,
} from './ui';
import { TaskCollaboration } from './TaskCollaboration';
export function TaskDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const task = useQuery({ queryKey: ['task', id], queryFn: () => api<Task>(`/tasks/${id}`) });
  return (
    <Dialog.Root
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <Dialog.Portal>
        <Dialog.Overlay className="drawer-overlay" />
        <Dialog.Content className="task-drawer" aria-describedby={undefined}>
          <div className="drawer-top">
            <Dialog.Title>{task.data?.key ?? 'Task details'}</Dialog.Title>
            <Dialog.Close asChild>
              <button className="icon-button" aria-label="Close task details">
                <X size={19} />
              </button>
            </Dialog.Close>
          </div>
          {task.isPending ? (
            <Loading />
          ) : task.isError ? (
            <ErrorState error={task.error} retry={() => task.refetch()} />
          ) : (
            <TaskDetails key={`${id}-${task.data.version}`} task={task.data} onClose={onClose} />
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
function TaskDetails({ task, onClose }: { task: Task; onClose: () => void }) {
  const client = useQueryClient(),
    toast = useToast();
  const { user } = useAuth();
  const [confirmDelete, setConfirmDelete] = useState(false),
    [editing, setEditing] = useState(false),
    [reason, setReason] = useState(''),
    [requesting, setRequesting] = useState(false);
  const members = useQuery({
    queryKey: ['members', task.projectId],
    queryFn: () => api<Member[]>(`/projects/${task.projectId}/members`),
  });
  const teams = useQuery({
    queryKey: ['teams', task.projectId],
    queryFn: () => api<Team[]>(`/teams?projectId=${task.projectId}`),
    retry: false,
  });
  const activity = useQuery({
    queryKey: ['activity', 'task', task.id],
    queryFn: () => api<Page<Activity>>(`/activity?taskId=${task.id}&size=15`),
  });
  const change = useMutation({
    mutationFn: ({
      endpoint,
      body,
      method = 'PATCH',
    }: {
      endpoint: string;
      body: unknown;
      method?: string;
    }) =>
      method === 'PUT'
        ? put(`/tasks/${task.id}${endpoint}`, body)
        : patch(`/tasks/${task.id}${endpoint}`, body),
    onSuccess: () => {
      client.invalidateQueries();
      toast('Task updated');
      setEditing(false);
    },
    onError: (e) => toast(e.message, 'error'),
  });
  const remove = useMutation({
    mutationFn: () => del(`/tasks/${task.id}`),
    onSuccess: () => {
      client.invalidateQueries();
      onClose();
      toast('Task deleted');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  const reassign = useMutation({
    mutationFn: () => post(`/tasks/${task.id}/reassignment-request`, { reason }),
    onSuccess: () => {
      client.invalidateQueries();
      setRequesting(false);
      toast('Request sent to the project owner');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    change.mutate({
      endpoint: '',
      method: 'PUT',
      body: { ...f, dueDate: f.dueDate || null, teamId: f.teamId || null, version: task.version },
    });
  }
  return (
    <div className="drawer-content">
      <Link
        className="drawer-project"
        to={`/projects/${task.projectId}/overview`}
        onClick={onClose}
      >
        {task.projectName}
        <ArrowUpRight size={13} />
      </Link>
      {editing ? (
        <form className="form-stack" onSubmit={save}>
          <label>
            Title
            <input name="title" required defaultValue={task.title} maxLength={200} />
          </label>
          <label>
            Description
            <textarea
              name="description"
              rows={7}
              defaultValue={task.description ?? ''}
              maxLength={20000}
            />
          </label>
          <div className="form-grid">
            <label>
              Priority
              <select name="priority" defaultValue={task.priority}>
                {Object.entries(priorityLabels).map(([v, n]) => (
                  <option key={v} value={v}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Due date
              <input name="dueDate" type="date" defaultValue={task.dueDate ?? ''} />
            </label>
          </div>
          <label>
            Team
            <select name="teamId" defaultValue={task.teamId ?? ''}>
              <option value="">No team</option>
              {teams.data?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
          <FormError error={change.error} />
          <div className="form-actions">
            <Button type="button" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button variant="primary" busy={change.isPending}>
              Save changes
            </Button>
          </div>
        </form>
      ) : (
        <>
          <div className="drawer-title-row">
            <h2>{task.title}</h2>
            {task.permissions.edit && (
              <Button variant="ghost" onClick={() => setEditing(true)}>
                Edit
              </Button>
            )}
          </div>
          <div className="task-properties">
            <span>Status</span>
            <select
              aria-label="Task status"
              value={task.status}
              disabled={!task.permissions.edit || change.isPending}
              onChange={(e) =>
                change.mutate({
                  endpoint: '/status',
                  body: { status: e.target.value, version: task.version },
                })
              }
            >
              {Object.entries(statusLabels).map(([v, n]) => (
                <option key={v} value={v}>
                  {n}
                </option>
              ))}
            </select>
            <span>Priority</span>
            <span className={`priority priority-${task.priority.toLowerCase()}`}>
              {priorityLabels[task.priority]}
            </span>
            <span>Assignee</span>
            {task.permissions.assign ? (
              <select
                aria-label="Task assignee"
                value={task.assignee?.id ?? ''}
                disabled={change.isPending}
                onChange={(e) =>
                  change.mutate({
                    endpoint: '/assignee',
                    body: { assigneeId: e.target.value || null, version: task.version },
                  })
                }
              >
                <option value="">Unassigned</option>
                {members.data
                  ?.filter((m) => m.user.active && m.role !== 'VIEWER')
                  .map((m) => (
                    <option key={m.user.id} value={m.user.id}>
                      {m.user.fullName}
                    </option>
                  ))}
              </select>
            ) : (
              <div className="person-inline">
                {task.assignee ? (
                  <>
                    <Avatar person={task.assignee} />
                    {task.assignee.fullName}
                  </>
                ) : (
                  <>
                    Unassigned
                    {task.permissions.selfAssign && (
                      <Button
                        variant="ghost"
                        busy={change.isPending}
                        onClick={() =>
                          change.mutate({
                            endpoint: '/assignee',
                            body: { assigneeId: user!.id, version: task.version },
                          })
                        }
                      >
                        Assign to me
                      </Button>
                    )}
                  </>
                )}
              </div>
            )}
            <span>Due date</span>
            <span className={task.overdue ? 'overdue' : ''}>
              <Clock3 size={14} />
              {date(task.dueDate, { year: 'numeric' })}
              {task.overdue ? ' · Overdue' : ''}
            </span>
            <span>Team</span>
            <span>{task.teamName || 'No team'}</span>
            <span>Created by</span>
            <div className="person-inline">
              <Avatar person={task.creator} />
              {task.creator.fullName}
            </div>
          </div>
          <section className="drawer-section">
            <h3>Description</h3>
            <p className="task-description">{task.description || 'No description yet.'}</p>
          </section>
        </>
      )}
      <TaskCollaboration task={task} />
      <section className="drawer-section">
        <h3>Activity</h3>
        {activity.isPending ? (
          <Loading rows={2} />
        ) : activity.isError ? (
          <ErrorState error={activity.error} retry={() => activity.refetch()} />
        ) : (
          <div className="activity-list">
            {activity.data.content.map((a) => (
              <div className="activity-entry" key={a.id}>
                <span className="activity-mark" />
                <div>
                  <p>{a.description}</p>
                  <span>
                    {a.actorName} · {relative(a.createdAt)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
      {task.permissions.edit && (
        <section className="drawer-section">
          {requesting ? (
            <form
              className="form-stack"
              onSubmit={(e) => {
                e.preventDefault();
                reassign.mutate();
              }}
            >
              <label>
                Reason for reassignment
                <textarea
                  required
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  maxLength={2000}
                />
              </label>
              <div className="form-actions">
                <Button type="button" onClick={() => setRequesting(false)}>
                  Cancel
                </Button>
                <Button busy={reassign.isPending}>Send request</Button>
              </div>
            </form>
          ) : (
            <Button variant="ghost" onClick={() => setRequesting(true)}>
              <UserRound size={15} />
              Request reassignment
            </Button>
          )}
        </section>
      )}
      <div className="drawer-bottom">
        <span>Updated {relative(task.updatedAt)}</span>
        {task.permissions.delete && (
          <Button variant="ghost" onClick={() => setConfirmDelete(true)}>
            <Trash2 size={15} />
            Delete task
          </Button>
        )}
      </div>
      <Confirm
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => remove.mutate()}
        busy={remove.isPending}
        title="Delete this task?"
        description={`“${task.title}” and its collaboration history will be permanently removed.`}
      />
    </div>
  );
}
