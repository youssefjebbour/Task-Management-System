import { useState } from 'react';
import { useParams, useOutletContext } from 'react-router-dom';
import {
  DndContext,
  PointerSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  useDraggable,
  useDroppable,
  DragOverlay,
  type DragEndEvent,
} from '@dnd-kit/core';
import { CSS } from '@dnd-kit/utilities';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CalendarDays, GripVertical, Plus } from 'lucide-react';
import { api, patch } from '../lib/api';
import type { Page, Task, TaskStatus, Project } from '../lib/types';
import { date, statusLabels } from '../lib/format';
import { useAppUI } from '../components/AppLayout';
import { Avatar, PriorityBadge, Button, Loading, ErrorState, useToast } from '../components/ui';
const statuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];
export function BoardPage() {
  const { id } = useParams();
  const project = useOutletContext<Project>();
  const client = useQueryClient(),
    toast = useToast();
  const [active, setActive] = useState<Task | null>(null);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor),
  );
  const change = useMutation({
    mutationFn: ({ task, status }: { task: Task; status: TaskStatus }) =>
      patch(`/tasks/${task.id}/status`, { status, version: task.version }),
    onSuccess: () => client.invalidateQueries(),
    onError: (e) => toast(e.message, 'error'),
  });
  function drop(e: DragEndEvent) {
    const task = e.active.data.current?.task as Task;
    const status = e.over?.id as TaskStatus;
    setActive(null);
    if (task && statuses.includes(status) && task.status !== status)
      change.mutate({ task, status });
  }
  return (
    <>
      <div className="board-caption">
        <span>Work in motion</span>
        <span>Drag a task, or use its status menu.</span>
      </div>
      <DndContext
        sensors={sensors}
        onDragStart={(e) => setActive(e.active.data.current?.task)}
        onDragEnd={drop}
        onDragCancel={() => setActive(null)}
      >
        <div className="kanban-board">
          {statuses.map((status) => (
            <BoardColumn
              key={status}
              id={id!}
              status={status}
              canCreate={project.canCreateTask}
              onStatus={(task, next) => change.mutate({ task, status: next })}
              busy={change.isPending}
            />
          ))}
        </div>
        <DragOverlay>
          {active ? (
            <div className="kanban-card drag-preview">
              <strong>{active.title}</strong>
              <PriorityBadge priority={active.priority} />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>
    </>
  );
}
function BoardColumn({
  id,
  status,
  canCreate,
  onStatus,
  busy,
}: {
  id: string;
  status: TaskStatus;
  canCreate: boolean;
  onStatus: (t: Task, s: TaskStatus) => void;
  busy: boolean;
}) {
  const { createTask } = useAppUI();
  const { setNodeRef, isOver } = useDroppable({ id: status });
  const query = useInfiniteQuery({
    queryKey: ['tasks', 'board', id, status],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api<Page<Task>>(
        `/tasks?scope=all&projectId=${id}&status=${status}&size=20&page=${pageParam}&sort=priority&direction=desc`,
      ),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
  return (
    <section ref={setNodeRef} className={`kanban-column ${isOver ? 'drag-over' : ''}`}>
      <div className="kanban-heading">
        <h2>
          <span className={`status-dot dot-${status.toLowerCase()}`} />
          {statusLabels[status]}
          <span>{query.data?.pages[0].totalElements ?? 0}</span>
        </h2>
        {canCreate && status === 'TODO' && (
          <button
            className="icon-button"
            aria-label="Add task to project"
            onClick={() => createTask(id)}
          >
            <Plus size={15} />
          </button>
        )}
      </div>
      {query.isPending ? (
        <Loading rows={2} />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : query.data.pages[0].totalElements === 0 ? (
        <p className="kanban-empty">
          No tasks {status === 'COMPLETED' ? 'completed yet' : 'here yet'}.
        </p>
      ) : (
        query.data.pages
          .flatMap((p) => p.content)
          .map((t) => <BoardCard key={t.id} task={t} onStatus={onStatus} busy={busy} />)
      )}
      {query.hasNextPage && (
        <Button busy={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>
          Load more
        </Button>
      )}
    </section>
  );
}
function BoardCard({
  task,
  onStatus,
  busy,
}: {
  task: Task;
  onStatus: (t: Task, s: TaskStatus) => void;
  busy: boolean;
}) {
  const { openTask } = useAppUI();
  const { setNodeRef, attributes, listeners, transform, isDragging } = useDraggable({
    id: task.id,
    data: { task },
    disabled: !task.permissions.edit || busy,
  });
  return (
    <article
      ref={setNodeRef}
      className={`kanban-card ${isDragging ? 'dragging' : ''}`}
      style={{ transform: CSS.Translate.toString(transform) }}
    >
      <div className="kanban-card-id">
        <span>{task.key}</span>
        {task.permissions.edit && (
          <button
            className="drag-handle"
            {...listeners}
            {...attributes}
            aria-label={`Move ${task.title}`}
          >
            <GripVertical size={15} />
          </button>
        )}
      </div>
      <button className="kanban-title" onClick={() => openTask(task.id)}>
        {task.title}
      </button>
      <PriorityBadge priority={task.priority} />
      <div className="kanban-card-bottom">
        <span className={task.overdue ? 'overdue' : ''}>
          <CalendarDays size={13} />
          {date(task.dueDate)}
        </span>
        {task.assignee && <Avatar person={task.assignee} />}
      </div>
      {task.permissions.edit && (
        <select
          className="kanban-status-select"
          aria-label={`Status of ${task.title}`}
          value={task.status}
          disabled={busy}
          onChange={(e) => onStatus(task, e.target.value as TaskStatus)}
        >
          {statuses.map((s) => (
            <option key={s} value={s}>
              {statusLabels[s]}
            </option>
          ))}
        </select>
      )}
    </article>
  );
}
