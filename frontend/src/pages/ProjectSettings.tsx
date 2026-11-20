import { useState, type FormEvent } from 'react';
import { useOutletContext, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, put, post, del } from '../lib/api';
import type { Project, Member } from '../lib/types';
import { projectStatusLabels } from '../lib/format';
import { Button, Confirm, FormError, useToast } from '../components/ui';
export function ProjectSettings() {
  const project = useOutletContext<Project>();
  const client = useQueryClient(),
    toast = useToast(),
    navigate = useNavigate();
  const [confirm, setConfirm] = useState<'delete' | 'transfer' | null>(null),
    [owner, setOwner] = useState('');
  const members = useQuery({
    queryKey: ['members', project.id],
    queryFn: () => api<Member[]>(`/projects/${project.id}/members`),
  });
  const update = useMutation({
    mutationFn: (body: unknown) => put(`/projects/${project.id}`, body),
    onSuccess: () => {
      client.invalidateQueries();
      toast('Project settings saved');
    },
  });
  const transfer = useMutation({
    mutationFn: () => post(`/projects/${project.id}/transfer`, { userId: owner }),
    onSuccess: () => {
      client.invalidateQueries();
      setConfirm(null);
      toast('Ownership transferred');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  const remove = useMutation({
    mutationFn: () => del(`/projects/${project.id}`),
    onSuccess: () => {
      client.invalidateQueries();
      navigate('/projects');
      toast('Project deleted');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const f = Object.fromEntries(form);
    update.mutate({
      ...f,
      startDate: f.startDate || null,
      deadline: f.deadline || null,
      membersCanCreate: form.has('membersCanCreate'),
      membersCanSelfAssign: form.has('membersCanSelfAssign'),
      membersCanAssign: form.has('membersCanAssign'),
      blockIncompleteDependencies: form.has('blockIncompleteDependencies'),
      version: project.version,
    });
  }
  if (!project.canManage) return <p>You do not have permission to manage this project.</p>;
  return (
    <div className="project-settings">
      <form onSubmit={save} key={project.version}>
        <section className="settings-section">
          <div>
            <h2>Project details</h2>
            <p>Keep the goal and timeline clear for everyone.</p>
          </div>
          <div className="settings-panel form-stack">
            <label>
              Name
              <input name="name" required maxLength={120} defaultValue={project.name} />
            </label>
            <label>
              Description
              <textarea
                name="description"
                rows={4}
                maxLength={10000}
                defaultValue={project.description ?? ''}
              />
            </label>
            <label>
              Status
              <select name="status" defaultValue={project.status}>
                {Object.entries(projectStatusLabels).map(([v, n]) => (
                  <option key={v} value={v}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-grid">
              <label>
                Start date
                <input name="startDate" type="date" defaultValue={project.startDate ?? ''} />
              </label>
              <label>
                Deadline
                <input name="deadline" type="date" defaultValue={project.deadline ?? ''} />
              </label>
            </div>
          </div>
        </section>
        <section className="settings-section">
          <div>
            <h2>Working agreements</h2>
            <p>These permissions are enforced for every request to the API.</p>
          </div>
          <div className="settings-panel policy-list">
            {[
              {
                key: 'membersCanCreate',
                title: 'Members can create tasks',
                description: 'Let members contribute new work.',
              },
              {
                key: 'membersCanSelfAssign',
                title: 'Members can self-assign',
                description: 'Let members pick up unassigned tasks.',
              },
              {
                key: 'membersCanAssign',
                title: 'Members can assign others',
                description: 'Off by default. Enables assignment to eligible project members.',
              },
              {
                key: 'blockIncompleteDependencies',
                title: 'Require dependencies to finish first',
                description: 'Keep blocked tasks from completing too early.',
              },
            ].map((p) => (
              <label className="policy-row" key={p.key}>
                <span>
                  <strong>{p.title}</strong>
                  <small>{p.description}</small>
                </span>
                <input
                  type="checkbox"
                  name={p.key}
                  defaultChecked={project[p.key as keyof Project] as boolean}
                />
              </label>
            ))}
          </div>
        </section>
        <FormError error={update.error} />
        <div className="form-actions">
          <Button variant="primary" busy={update.isPending}>
            Save project settings
          </Button>
        </div>
      </form>
      {project.canTransfer && (
        <>
          <section className="settings-section">
            <div>
              <h2>Transfer ownership</h2>
              <p>The new owner gains full control. You become a manager.</p>
            </div>
            <div className="settings-panel form-stack">
              <label>
                New owner
                <select value={owner} onChange={(e) => setOwner(e.target.value)}>
                  <option value="">Choose a member</option>
                  {members.data
                    ?.filter((m) => m.user.id !== project.owner.id && m.user.active)
                    .map((m) => (
                      <option key={m.id} value={m.user.id}>
                        {m.user.fullName}
                      </option>
                    ))}
                </select>
              </label>
              <div className="form-actions">
                <Button
                  disabled={!owner || project.status === 'ARCHIVED'}
                  onClick={() => setConfirm('transfer')}
                >
                  Transfer ownership
                </Button>
              </div>
            </div>
          </section>
          <section className="settings-section danger-zone">
            <div>
              <h2>Delete project</h2>
              <p>
                Archive this project first. Deleting permanently removes its tasks and project
                records.
              </p>
            </div>
            <Button
              variant="danger"
              disabled={project.status !== 'ARCHIVED'}
              onClick={() => setConfirm('delete')}
            >
              Delete project
            </Button>
          </section>
        </>
      )}
      <Confirm
        open={!!confirm}
        onClose={() => setConfirm(null)}
        title={
          confirm === 'delete' ? 'Permanently delete this project?' : 'Transfer project ownership?'
        }
        description={
          confirm === 'delete'
            ? `“${project.name}” and all its tasks will be deleted. This cannot be undone.`
            : 'The selected member will own this project and you will become a manager.'
        }
        busy={remove.isPending || transfer.isPending}
        onConfirm={() => (confirm === 'delete' ? remove.mutate() : transfer.mutate())}
      />
    </div>
  );
}
