import { useState, type FormEvent } from 'react';
import { useOutletContext } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { api, post, patch, del } from '../lib/api';
import type { Project, Member, ProjectRole } from '../lib/types';
import { roleLabels, date } from '../lib/format';
import { useAuth } from '../lib/auth';
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
} from '../components/ui';
export function MembersPage() {
  const project = useOutletContext<Project>();
  const { user } = useAuth();
  const client = useQueryClient(),
    toast = useToast();
  const [adding, setAdding] = useState(false),
    [removing, setRemoving] = useState<Member | null>(null);
  const query = useQuery({
    queryKey: ['members', project.id],
    queryFn: () => api<Member[]>(`/projects/${project.id}/members`),
  });
  const roles = (Object.keys(roleLabels) as ProjectRole[]).filter(
    (r) => r !== 'OWNER' && (r !== 'MANAGER' || project.canTransfer),
  );
  const add = useMutation({
    mutationFn: (body: unknown) => post(`/projects/${project.id}/members`, body),
    onSuccess: () => {
      client.invalidateQueries();
      setAdding(false);
      toast('Member added');
    },
  });
  const change = useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) =>
      patch(`/projects/${project.id}/members/${id}`, { role }),
    onSuccess: () => {
      client.invalidateQueries();
      toast('Role updated');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  const remove = useMutation({
    mutationFn: () => del(`/projects/${project.id}/members/${removing!.user.id}`),
    onSuccess: () => {
      client.invalidateQueries();
      setRemoving(null);
      toast('Member removed');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    add.mutate(Object.fromEntries(new FormData(e.currentTarget)));
  }
  return (
    <div className="members-page">
      <div className="section-title">
        <div>
          <h2>Project members</h2>
          <p className="muted">The people moving this project forward.</p>
        </div>
        {project.canManage && project.status !== 'ARCHIVED' && (
          <Button onClick={() => setAdding(true)}>
            <Plus size={15} />
            Add member
          </Button>
        )}
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : (
        <div className="member-list">
          {query.data.map((m) => (
            <div className="member-row" key={m.id}>
              <Avatar person={m.user} size="medium" />
              <div className="member-name">
                <strong>
                  {m.user.fullName}
                  {m.user.id === user?.id && <span> (you)</span>}
                </strong>
                <span>
                  {m.user.jobTitle || `@${m.user.username}`}
                  {!m.user.active ? ' · Deactivated' : ''}
                </span>
              </div>
              <span className="joined-date">Joined {date(m.joinedAt)}</span>
              {project.canManage &&
              m.role !== 'OWNER' &&
              (m.role !== 'MANAGER' || project.canTransfer) ? (
                <>
                  <select
                    aria-label={`Role for ${m.user.fullName}`}
                    value={m.role}
                    disabled={change.isPending || project.status === 'ARCHIVED'}
                    onChange={(e) => change.mutate({ id: m.user.id, role: e.target.value })}
                  >
                    {roles.map((r) => (
                      <option key={r} value={r}>
                        {roleLabels[r]}
                      </option>
                    ))}
                  </select>
                  <button
                    className="icon-button"
                    aria-label={`Remove ${m.user.fullName}`}
                    disabled={project.status === 'ARCHIVED'}
                    onClick={() => setRemoving(m)}
                  >
                    <Trash2 size={15} />
                  </button>
                </>
              ) : (
                <span className="member-role">{roleLabels[m.role]}</span>
              )}
            </div>
          ))}
        </div>
      )}
      <div className="permission-note">
        <h3>Roles keep responsibilities clear</h3>
        <p>
          Owners manage the project. Managers organize the work. Team leads coordinate their teams.
          Members update assigned work; viewers have read-only access.
        </p>
      </div>
      <Modal
        open={adding}
        onClose={() => setAdding(false)}
        title="Add a project member"
        description="Invite an existing TaskFlow account by email."
      >
        <form className="form-stack" onSubmit={submit}>
          <label>
            Email address
            <input type="email" name="email" required placeholder="teammate@company.com" />
          </label>
          <label>
            Project role
            <select name="role" defaultValue="MEMBER">
              {roles.map((r) => (
                <option key={r} value={r}>
                  {roleLabels[r]}
                </option>
              ))}
            </select>
          </label>
          <FormError error={add.error} />
          <div className="form-actions">
            <Button type="button" onClick={() => setAdding(false)}>
              Cancel
            </Button>
            <Button variant="primary" busy={add.isPending}>
              Add member
            </Button>
          </div>
        </form>
      </Modal>
      <Confirm
        open={!!removing}
        onClose={() => setRemoving(null)}
        onConfirm={() => remove.mutate()}
        busy={remove.isPending}
        title="Remove this member?"
        description={`${removing?.user.fullName} will lose project access. Their tasks will become unassigned.`}
      />
    </div>
  );
}
