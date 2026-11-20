import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Search, Users, Trash2, X } from 'lucide-react';
import { api, post, put, del } from '../lib/api';
import type { Person, Project, Page, Team, Member } from '../lib/types';
import {
  Avatar,
  Button,
  Confirm,
  PageHeading,
  Loading,
  ErrorState,
  Empty,
  FormError,
  Modal,
  useToast,
} from '../components/ui';
export function TeamPage() {
  const [tab, setTab] = useState<'directory' | 'teams'>('directory'),
    [q, setQ] = useState(''),
    [selected, setSelected] = useState(''),
    [adding, setAdding] = useState(false),
    [editing, setEditing] = useState<Team | null>(null),
    [remove, setRemove] = useState<Team | null>(null);
  const client = useQueryClient(),
    toast = useToast();
  const people = useQuery({
    queryKey: ['directory'],
    queryFn: () => api<Person[]>('/users/directory'),
  });
  const projects = useQuery({
    queryKey: ['projects', 'navigation'],
    queryFn: () => api<Page<Project>>('/projects?size=100'),
  });
  const projectId = selected || projects.data?.content[0]?.id || '';
  const project = projects.data?.content.find((p) => p.id === projectId);
  const teams = useQuery({
    queryKey: ['teams', projectId],
    queryFn: () => api<Team[]>(`/teams?projectId=${projectId}`),
    enabled: !!projectId,
  });
  const members = useQuery({
    queryKey: ['members', projectId],
    queryFn: () => api<Member[]>(`/projects/${projectId}/members`),
    enabled: !!projectId,
  });
  const create = useMutation({
    mutationFn: (name: string) => post('/teams', { name, projectId }),
    onSuccess: () => {
      client.invalidateQueries();
      setAdding(false);
      toast('Team created');
    },
  });
  const membership = useMutation({
    mutationFn: ({ team, user, remove }: { team: string; user: string; remove?: boolean }) =>
      remove
        ? del(`/teams/${team}/members/${user}`)
        : post(`/teams/${team}/members`, { userId: user }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['teams'] }),
    onError: (e) => toast(e.message, 'error'),
  });
  const rename = useMutation({
    mutationFn: (name: string) => put(`/teams/${editing!.id}`, { name }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['teams'] });
      setEditing(null);
      toast('Team renamed');
    },
  });
  const deleting = useMutation({
    mutationFn: () => del(`/teams/${remove!.id}`),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['teams'] });
      setRemove(null);
      toast('Team deleted');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  const canManage = project?.canManage && project.status !== 'ARCHIVED';
  return (
    <div className="page">
      <PageHeading
        title="Team"
        description="Good work starts with good people."
        actions={
          tab === 'teams' &&
          canManage && (
            <Button variant="primary" onClick={() => setAdding(true)}>
              <Plus size={16} />
              Create team
            </Button>
          )
        }
      />
      <div className="tabs">
        <button className={tab === 'directory' ? 'active' : ''} onClick={() => setTab('directory')}>
          People
        </button>
        <button className={tab === 'teams' ? 'active' : ''} onClick={() => setTab('teams')}>
          Project teams
        </button>
      </div>
      {tab === 'directory' ? (
        <>
          <div className="team-search search-field">
            <Search size={16} />
            <input
              aria-label="Search people"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search your teammates…"
            />
          </div>
          {people.isPending ? (
            <Loading />
          ) : people.isError ? (
            <ErrorState error={people.error} retry={() => people.refetch()} />
          ) : (
            <div className="directory-grid">
              {people.data
                .filter((p) =>
                  `${p.fullName} ${p.jobTitle ?? ''}`.toLowerCase().includes(q.toLowerCase()),
                )
                .map((p) => (
                  <article key={p.id} className="person-card">
                    <Avatar person={p} size="large" />
                    <h2>{p.fullName}</h2>
                    <p>{p.jobTitle || 'Team member'}</p>
                    <span>@{p.username}</span>
                    {!p.active && <span className="muted">Deactivated</span>}
                  </article>
                ))}
            </div>
          )}
        </>
      ) : (
        <>
          <div className="project-toolbar team-project-select">
            <label>
              Project
              <select value={projectId} onChange={(e) => setSelected(e.target.value)}>
                {projects.data?.content.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {!projectId ? (
            <Empty
              title="Create a project first"
              description="Teams help you organize people within a project."
            />
          ) : teams.isPending ? (
            <Loading />
          ) : teams.isError ? (
            <ErrorState error={teams.error} retry={() => teams.refetch()} />
          ) : teams.data.length ? (
            <div className="teams-grid">
              {teams.data.map((team) => (
                <section className="team-panel" key={team.id}>
                  <div className="section-title">
                    <h2>{team.name}</h2>
                    {canManage && (
                      <div className="inline-actions">
                        <Button variant="ghost" onClick={() => setEditing(team)}>
                          Rename
                        </Button>
                        <button
                          className="icon-button"
                          aria-label={`Delete ${team.name}`}
                          onClick={() => setRemove(team)}
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    )}
                  </div>
                  {team.members.length ? (
                    team.members.map((p) => (
                      <div className="team-person" key={p.id}>
                        <Avatar person={p} />
                        <span>{p.fullName}</span>
                        {canManage && (
                          <button
                            className="icon-button"
                            aria-label={`Remove ${p.fullName} from ${team.name}`}
                            onClick={() =>
                              membership.mutate({ team: team.id, user: p.id, remove: true })
                            }
                          >
                            <X size={14} />
                          </button>
                        )}
                      </div>
                    ))
                  ) : (
                    <p className="muted">No members yet.</p>
                  )}
                  {canManage && (
                    <select
                      aria-label={`Add member to ${team.name}`}
                      value=""
                      onChange={(e) => membership.mutate({ team: team.id, user: e.target.value })}
                      disabled={membership.isPending}
                    >
                      <option value="">Add a project member…</option>
                      {members.data
                        ?.filter(
                          (m) => m.user.active && !team.members.some((p) => p.id === m.user.id),
                        )
                        .map((m) => (
                          <option key={m.id} value={m.user.id}>
                            {m.user.fullName}
                          </option>
                        ))}
                    </select>
                  )}
                </section>
              ))}
            </div>
          ) : (
            <Empty
              title="Give your team a home"
              description="Create a team to organize responsibilities and keep team-lead assignments in scope."
            />
          )}
        </>
      )}
      <Modal
        open={adding || !!editing}
        onClose={() => {
          setAdding(false);
          setEditing(null);
        }}
        title={editing ? 'Rename team' : 'Create a team'}
        description="Teams belong to a project and define the scope for team leads."
      >
        <form
          className="form-stack"
          key={editing?.id ?? 'new'}
          onSubmit={(e: FormEvent<HTMLFormElement>) => {
            e.preventDefault();
            const name = String(new FormData(e.currentTarget).get('name'));
            editing ? rename.mutate(name) : create.mutate(name);
          }}
        >
          <label>
            Team name
            <input
              name="name"
              required
              maxLength={100}
              defaultValue={editing?.name ?? ''}
              placeholder="e.g. Product team"
            />
          </label>
          <FormError error={editing ? rename.error : create.error} />
          <div className="form-actions">
            <Button variant="primary" busy={create.isPending || rename.isPending}>
              {editing ? 'Save name' : 'Create team'}
            </Button>
          </div>
        </form>
      </Modal>
      <Confirm
        open={!!remove}
        onClose={() => setRemove(null)}
        title="Delete this team?"
        description={`“${remove?.name}” will be removed. Teams with assigned tasks must be emptied first.`}
        busy={deleting.isPending}
        onConfirm={() => deleting.mutate()}
      />
    </div>
  );
}
