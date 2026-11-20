import { useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { Search, Plus, SlidersHorizontal, List, ArrowDownWideNarrow, X } from 'lucide-react';
import { api, queryString } from '../lib/api';
import type { Page, Project, Task, TaskStatus, Summary, Person, Label } from '../lib/types';
import { priorityLabels, statusLabels } from '../lib/format';
import { useAppUI } from '../components/AppLayout';
import { Button, PageHeading, Empty, Loading, ErrorState, Pagination } from '../components/ui';
import { TaskTable } from '../components/TaskTable';
export function TasksPage({
  mode = 'mine',
  projectId,
}: {
  mode?: 'mine' | 'completed' | 'search' | 'project';
  projectId?: string;
}) {
  const { createTask, createProject } = useAppUI();
  const [status, setStatus] = useState(''),
    [scope, setScope] = useState(mode === 'project' || mode === 'search' ? 'all' : 'mine'),
    [q, setQ] = useState(''),
    [priority, setPriority] = useState(''),
    [sort, setSort] = useState('dueDate'),
    [page, setPage] = useState(0),
    [showFilters, setShowFilters] = useState(false),
    [projectFilter, setProjectFilter] = useState(''),
    [dueFrom, setDueFrom] = useState(''),
    [dueTo, setDueTo] = useState(''),
    [overdue, setOverdue] = useState(false),
    [direction, setDirection] = useState('asc');
  const [assigneeId, setAssigneeId] = useState(''),
    [labelId, setLabelId] = useState(''),
    [createdFrom, setCreatedFrom] = useState(''),
    [createdTo, setCreatedTo] = useState('');
  const query = {
    assigneeId,
    labelId,
    createdFrom,
    createdTo,
    scope,
    status: mode === 'completed' ? 'COMPLETED' : status,
    q,
    priority,
    sort,
    direction,
    page,
    size: 15,
    projectId: projectId || projectFilter,
    dueFrom,
    dueTo,
    overdue: overdue || undefined,
  };
  const tasks = useQuery({
    queryKey: ['tasks', query],
    queryFn: () => api<Page<Task>>(`/tasks?${queryString(query)}`),
    placeholderData: keepPreviousData,
  });
  const summary = useQuery({
    queryKey: ['summary', 'personal'],
    queryFn: () => api<Summary>('/reports/personal'),
    enabled: mode === 'mine',
  });
  const projects = useQuery({
    queryKey: ['projects', 'navigation'],
    queryFn: () => api<Page<Project>>('/projects?size=100'),
  });
  const people = useQuery({
    queryKey: ['directory'],
    queryFn: () => api<Person[]>('/users/directory'),
  });
  const labelProject = projectId || projectFilter;
  const labels = useQuery({
    queryKey: ['labels', labelProject],
    queryFn: () => api<Label[]>(`/projects/${labelProject}/labels`),
    enabled: !!labelProject,
  });
  const canCreate = projectId
    ? projects.data?.content.find((p) => p.id === projectId)?.canCreateTask
    : projects.data?.content.some((p) => p.canCreateTask);
  const title =
    mode === 'completed'
      ? 'Completed Tasks'
      : mode === 'search'
        ? 'Search your workspace'
        : mode === 'project'
          ? 'Project tasks'
          : 'My Tasks';
  const setFilter = (fn: () => void) => {
    fn();
    setPage(0);
  };
  return (
    <div className={mode === 'project' ? 'project-task-page' : 'page'}>
      {mode !== 'project' && (
        <PageHeading
          title={title}
          description={
            mode === 'mine'
              ? 'A clear view of what’s on your plate.'
              : mode === 'completed'
                ? 'Your completed work, ready to revisit.'
                : 'Find a task across the projects you can access.'
          }
          actions={
            mode !== 'completed' && (
              <Button
                variant="primary"
                disabled={projects.isPending}
                onClick={() => (canCreate ? createTask(projectId) : createProject())}
              >
                <Plus size={16} />
                {projects.isPending || canCreate ? 'New task' : 'Create project'}
              </Button>
            )
          }
        />
      )}
      {mode === 'mine' && summary.data && (
        <div className="task-focus">
          <span>
            <span className="focus-dot" />
            {summary.data.pending + summary.data.inProgress} open tasks
          </span>
          <span>{summary.data.dueToday} due today</span>
          {summary.data.overdue > 0 && (
            <button
              onClick={() =>
                setFilter(() => {
                  setOverdue(!overdue);
                  setShowFilters(true);
                })
              }
            >
              {summary.data.overdue} overdue
            </button>
          )}
          <span className="focus-spacer" />
          <span>Make space for your next step.</span>
        </div>
      )}
      {mode !== 'completed' && (
        <div className="tabs task-tabs">
          <button
            className={!status ? 'active' : ''}
            onClick={() => setFilter(() => setStatus(''))}
          >
            All tasks
          </button>
          {(['TODO', 'IN_PROGRESS', 'COMPLETED'] as TaskStatus[]).map((s) => (
            <button
              key={s}
              className={status === s ? 'active' : ''}
              onClick={() => setFilter(() => setStatus(s))}
            >
              {statusLabels[s]}
            </button>
          ))}
          {mode === 'project' && canCreate && (
            <Button className="tab-action" onClick={() => createTask(projectId)}>
              <Plus size={14} />
              Add task
            </Button>
          )}
        </div>
      )}
      <div className="table-toolbar">
        <div className="view-label">
          <List size={16} />
          List view
        </div>
        <div className="toolbar-divider" />
        <div className="search-field">
          <Search size={15} />
          <input
            aria-label="Search tasks"
            placeholder={mode === 'search' ? 'Search titles and descriptions…' : 'Search tasks…'}
            value={q}
            onChange={(e) => setFilter(() => setQ(e.target.value))}
          />
        </div>
        <div className="toolbar-spacer" />
        <Button
          className={showFilters ? 'is-selected' : ''}
          onClick={() => setShowFilters(!showFilters)}
        >
          <SlidersHorizontal size={15} />
          Filters
        </Button>
        <div className="sort-field">
          <ArrowDownWideNarrow size={15} />
          <select
            aria-label="Sort tasks"
            value={sort}
            onChange={(e) => setFilter(() => setSort(e.target.value))}
          >
            <option value="dueDate">Due date</option>
            <option value="priority">Priority</option>
            <option value="createdAt">Created date</option>
            <option value="updatedAt">Last updated</option>
            <option value="title">Title</option>
          </select>
        </div>
        <button
          className="icon-button"
          aria-label={direction === 'asc' ? 'Sort descending' : 'Sort ascending'}
          title={direction === 'asc' ? 'Ascending' : 'Descending'}
          onClick={() => setFilter(() => setDirection(direction === 'asc' ? 'desc' : 'asc'))}
        >
          <ArrowDownWideNarrow
            size={16}
            style={{ transform: direction === 'asc' ? 'rotate(180deg)' : undefined }}
          />
        </button>
      </div>
      {showFilters && (
        <div className="filter-panel">
          <label>
            Assignment
            <select value={scope} onChange={(e) => setFilter(() => setScope(e.target.value))}>
              <option value="mine">Assigned to me</option>
              <option value="created">Created by me</option>
              <option value="unassigned">Unassigned</option>
              <option value="all">All accessible tasks</option>
            </select>
          </label>
          <label>
            Priority
            <select value={priority} onChange={(e) => setFilter(() => setPriority(e.target.value))}>
              <option value="">Any priority</option>
              {Object.entries(priorityLabels).map(([v, n]) => (
                <option key={v} value={v}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          {!projectId && (
            <label>
              Project
              <select
                value={projectFilter}
                onChange={(e) =>
                  setFilter(() => {
                    setProjectFilter(e.target.value);
                    setLabelId('');
                  })
                }
              >
                <option value="">All projects</option>
                {projects.data?.content.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label>
            Assignee
            <select
              value={assigneeId}
              onChange={(e) =>
                setFilter(() => {
                  setAssigneeId(e.target.value);
                  setScope('all');
                })
              }
            >
              <option value="">Anyone</option>
              {people.data
                ?.filter((p) => p.active)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fullName}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Label
            <select
              value={labelId}
              disabled={!labelProject}
              onChange={(e) => setFilter(() => setLabelId(e.target.value))}
            >
              <option value="">{labelProject ? 'Any label' : 'Select a project first'}</option>
              {labels.data?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </label>
          {mode !== 'completed' && (
            <label>
              Status
              <select value={status} onChange={(e) => setFilter(() => setStatus(e.target.value))}>
                <option value="">Any status</option>
                {Object.entries(statusLabels).map(([v, n]) => (
                  <option key={v} value={v}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label>
            Created from
            <input
              type="date"
              value={createdFrom}
              onChange={(e) => setFilter(() => setCreatedFrom(e.target.value))}
            />
          </label>
          <label>
            Created to
            <input
              type="date"
              value={createdTo}
              onChange={(e) => setFilter(() => setCreatedTo(e.target.value))}
            />
          </label>
          <label>
            Due from
            <input
              type="date"
              value={dueFrom}
              onChange={(e) => setFilter(() => setDueFrom(e.target.value))}
            />
          </label>
          <label>
            Due to
            <input
              type="date"
              value={dueTo}
              onChange={(e) => setFilter(() => setDueTo(e.target.value))}
            />
          </label>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={overdue}
              onChange={(e) => setFilter(() => setOverdue(e.target.checked))}
            />
            Overdue only
          </label>
          <Button
            variant="ghost"
            onClick={() =>
              setFilter(() => {
                setPriority('');
                setProjectFilter('');
                setAssigneeId('');
                setLabelId('');
                setCreatedFrom('');
                setCreatedTo('');
                setDueFrom('');
                setDueTo('');
                setOverdue(false);
                setScope(mode === 'project' ? 'all' : 'mine');
                setStatus('');
              })
            }
          >
            <X size={14} />
            Reset
          </Button>
        </div>
      )}
      {tasks.isPending ? (
        <Loading />
      ) : tasks.isError ? (
        <ErrorState error={tasks.error} retry={() => tasks.refetch()} />
      ) : tasks.data.content.length ? (
        <>
          <TaskTable tasks={tasks.data.content} showProject={mode !== 'project'} />
          <Pagination data={tasks.data} onPage={setPage} />
        </>
      ) : (
        <Empty
          title={
            q || status || priority || overdue
              ? 'No matching tasks'
              : mode === 'completed'
                ? 'Your finished work will live here'
                : 'A fresh start'
          }
          description={
            q || status || priority || overdue
              ? 'Try changing your search or filters.'
              : 'Create a task, give it a clear owner, and take the next step.'
          }
          action={
            mode !== 'completed' && (
              <Button onClick={() => (canCreate ? createTask(projectId) : createProject())}>
                <Plus size={15} />
                {canCreate ? 'Create a task' : 'Create a project'}
              </Button>
            )
          }
        />
      )}
    </div>
  );
}
