import { useState } from 'react';
import { Link, NavLink, Outlet, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, Plus, Search, CalendarDays, FolderKanban } from 'lucide-react';
import { api, queryString } from '../lib/api';
import type { Page, Project, Activity } from '../lib/types';
import { date, projectStatusLabels, relative } from '../lib/format';
import { useAppUI } from '../components/AppLayout';
import {
  Button,
  PageHeading,
  ErrorState,
  Loading,
  Empty,
  Pagination,
  Avatar,
} from '../components/ui';
export function ProjectsPage() {
  const { createProject } = useAppUI();
  const [q, setQ] = useState(''),
    [status, setStatus] = useState(''),
    [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['projects', 'list', q, status, page],
    queryFn: () => api<Page<Project>>(`/projects?${queryString({ q, status, page, size: 12 })}`),
  });
  return (
    <div className="page">
      <PageHeading
        title="Projects"
        description="Shared goals. Clear next steps."
        actions={
          <Button variant="primary" onClick={createProject}>
            <Plus size={16} />
            New project
          </Button>
        }
      />
      <div className="project-toolbar">
        <div className="search-field">
          <Search size={16} />
          <input
            aria-label="Search projects"
            placeholder="Find a project…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <select
          aria-label="Project status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {Object.entries(projectStatusLabels).map(([v, n]) => (
            <option key={v} value={v}>
              {n}
            </option>
          ))}
        </select>
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : query.data.content.length ? (
        <>
          <div className="project-grid">
            {query.data.content.map((p) => (
              <Link className="project-card" to={`/projects/${p.id}/overview`} key={p.id}>
                <div className="project-card-top">
                  <span className="project-key">
                    <FolderKanban size={16} />
                    {p.projectKey}
                  </span>
                  <ArrowUpRight size={17} />
                </div>
                <h2>{p.name}</h2>
                <p>{p.description || 'A shared space for your team’s next project.'}</p>
                <div className="project-progress-label">
                  <span>{projectStatusLabels[p.status]}</span>
                  <span>
                    {p.totalTasks ? Math.round((p.completedTasks / p.totalTasks) * 100) : 0}%
                  </span>
                </div>
                <progress value={p.completedTasks} max={p.totalTasks || 1} />
                <div className="project-card-bottom">
                  <span>
                    {p.completedTasks} / {p.totalTasks} tasks
                  </span>
                  <span>
                    <CalendarDays size={13} />
                    {p.deadline ? date(p.deadline) : 'No deadline'}
                  </span>
                </div>
                <div className="project-card-owner">
                  <Avatar person={p.owner} />
                  <span>{p.owner.fullName}</span>
                  <span>{p.role?.toLowerCase().replaceAll('_', ' ') ?? 'Admin'}</span>
                </div>
              </Link>
            ))}
          </div>
          <Pagination data={query.data} onPage={setPage} />
        </>
      ) : (
        <Empty
          title="Bring your next project here"
          description="Create a shared space, invite your team, and give every task a home."
          action={
            <Button onClick={createProject}>
              <Plus size={15} />
              Create project
            </Button>
          }
        />
      )}
    </div>
  );
}
export function ProjectLayout() {
  const { id } = useParams();
  const { createTask } = useAppUI();
  const project = useQuery({
    queryKey: ['project', id],
    queryFn: () => api<Project>(`/projects/${id}`),
  });
  if (project.isPending)
    return (
      <div className="page">
        <Loading />
      </div>
    );
  if (project.isError)
    return (
      <div className="page">
        <ErrorState error={project.error} retry={() => project.refetch()} />
      </div>
    );
  return (
    <div className="page project-page">
      <PageHeading
        title={project.data.name}
        description={
          <>
            <span>{project.data.projectKey}</span>
            <span className="meta-separator">·</span>
            {projectStatusLabels[project.data.status]}
            <span className="meta-separator">·</span>
            {project.data.role?.toLowerCase().replaceAll('_', ' ') ?? 'Administrator'}
          </>
        }
        actions={
          project.data.canCreateTask && (
            <Button variant="primary" onClick={() => createTask(id)}>
              <Plus size={16} />
              New task
            </Button>
          )
        }
      />
      <div className="tabs project-tabs">
        {[
          'overview',
          'board',
          'list',
          'members',
          ...(project.data.canManage ? ['settings'] : []),
        ].map((tab) => (
          <NavLink key={tab} to={`/projects/${id}/${tab}`}>
            {tab[0].toUpperCase() + tab.slice(1)}
          </NavLink>
        ))}
      </div>
      <Outlet context={project.data} />
    </div>
  );
}
export function ProjectOverview() {
  const { id } = useParams();
  const project = useQuery({
    queryKey: ['project', id],
    queryFn: () => api<Project>(`/projects/${id}`),
  });
  const activity = useQuery({
    queryKey: ['activity', 'project', id],
    queryFn: () => api<Page<Activity>>(`/activity?projectId=${id}&size=6`),
  });
  const { openTask } = useAppUI();
  if (!project.data) return null;
  const p = project.data;
  return (
    <div className="project-overview">
      <section className="project-brief">
        <div className="section-title">
          <h2>About this project</h2>
          <span className="project-key">{p.projectKey}</span>
        </div>
        <p>{p.description || 'No project description yet.'}</p>
        <div className="project-facts">
          <div>
            <span>Project owner</span>
            <div className="person-inline">
              <Avatar person={p.owner} />
              {p.owner.fullName}
            </div>
          </div>
          <div>
            <span>Start date</span>
            <strong>{p.startDate ? date(p.startDate, { year: 'numeric' }) : 'Not set'}</strong>
          </div>
          <div>
            <span>Deadline</span>
            <strong>{p.deadline ? date(p.deadline, { year: 'numeric' }) : 'Not set'}</strong>
          </div>
        </div>
        <div className="project-progress-label">
          <h3>Progress</h3>
          <span>
            {p.totalTasks ? Math.round((p.completedTasks / p.totalTasks) * 100) : 0}% complete
          </span>
        </div>
        <progress value={p.completedTasks} max={p.totalTasks || 1} />
        <p className="muted">
          {p.completedTasks} of {p.totalTasks} tasks completed
        </p>
        <div className="overview-links">
          <Link to={`/projects/${id}/board`}>
            Open board <ArrowUpRight size={15} />
          </Link>
          <Link to={`/projects/${id}/members`}>
            Meet the team <ArrowUpRight size={15} />
          </Link>
          {p.canReport && (
            <Link to={`/reports?project=${id}`}>
              View report <ArrowUpRight size={15} />
            </Link>
          )}
        </div>
      </section>
      <section className="project-recent">
        <div className="section-title">
          <h2>Recent activity</h2>
        </div>
        {activity.isPending ? (
          <Loading rows={3} />
        ) : activity.isError ? (
          <ErrorState error={activity.error} retry={() => activity.refetch()} />
        ) : activity.data.content.length ? (
          <div className="activity-list">
            {activity.data.content.map((a) => (
              <div className="activity-entry" key={a.id}>
                <span className="activity-mark" />
                <div>
                  {a.taskId ? (
                    <button onClick={() => openTask(a.taskId!)}>{a.description}</button>
                  ) : (
                    <p>{a.description}</p>
                  )}
                  <span>
                    {a.actorName} · {relative(a.createdAt)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Empty
            title="A new beginning"
            description="Project activity will appear as your team gets to work."
          />
        )}
      </section>
    </div>
  );
}
