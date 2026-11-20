import { ArrowUpRight, Check, Circle, Clock3 } from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { patch } from '../lib/api';
import { date } from '../lib/format';
import type { Task } from '../lib/types';
import { useAppUI } from './AppLayout';
import { Avatar, PriorityBadge, StatusBadge, useToast } from './ui';
export function TaskTable({ tasks, showProject = true }: { tasks: Task[]; showProject?: boolean }) {
  const { openTask } = useAppUI();
  const client = useQueryClient(),
    toast = useToast();
  const complete = useMutation({
    mutationFn: (task: Task) =>
      patch(`/tasks/${task.id}/status`, {
        status: task.status === 'COMPLETED' ? 'TODO' : 'COMPLETED',
        version: task.version,
      }),
    onSuccess: () => {
      client.invalidateQueries();
    },
    onError: (e) => toast(e.message, 'error'),
  });
  return (
    <div className="task-table-wrap">
      <table className="task-table">
        <thead>
          <tr>
            <th className="check-cell">
              <span className="sr-only">Complete</span>
            </th>
            <th>Task name</th>
            <th>Priority</th>
            <th>Status</th>
            <th>Assignee</th>
            <th className="date-cell">Due date</th>
            <th className="open-cell">
              <span className="sr-only">Open</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id}>
              <td className="check-cell">
                <button
                  aria-label={
                    task.status === 'COMPLETED' ? `Reopen ${task.title}` : `Complete ${task.title}`
                  }
                  className={`task-checkbox ${task.status === 'COMPLETED' ? 'checked' : ''}`}
                  disabled={!task.permissions.edit || complete.isPending}
                  onClick={() => complete.mutate(task)}
                >
                  {task.status === 'COMPLETED' && <Check size={12} />}
                </button>
              </td>
              <td className="task-name-cell">
                <button className="task-title" onClick={() => openTask(task.id)}>
                  {task.title}
                </button>
                <div className="task-subline">
                  <span>{task.key}</span>
                  {showProject && (
                    <>
                      <span className="meta-separator">·</span>
                      <span>{task.projectName}</span>
                    </>
                  )}
                </div>
              </td>
              <td className="priority-cell">
                <PriorityBadge priority={task.priority} />
              </td>
              <td className="status-cell">
                <StatusBadge status={task.status} />
              </td>
              <td className="assignee-cell">
                {task.assignee ? (
                  <div className="person-inline">
                    <Avatar person={task.assignee} />
                    <span>{task.assignee.fullName}</span>
                  </div>
                ) : (
                  <span className="unassigned">
                    <Circle size={23} strokeWidth={1} />
                    <span>Unassigned</span>
                  </span>
                )}
              </td>
              <td className={`date-cell ${task.overdue ? 'overdue' : ''}`}>
                {task.overdue && <Clock3 size={12} />}
                <span>{date(task.dueDate)}</span>
              </td>
              <td className="open-cell">
                <button
                  className="icon-button"
                  aria-label={`Open ${task.title}`}
                  onClick={() => openTask(task.id)}
                >
                  <ArrowUpRight size={16} />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
