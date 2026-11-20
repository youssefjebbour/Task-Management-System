import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowUpRight, Plus } from 'lucide-react';
import { api } from '../lib/api';
import type { Summary, Page, Task, Activity } from '../lib/types';
import { date, relative, statusLabels, priorityLabels } from '../lib/format';
import { useAppUI } from '../components/AppLayout';
import { Button, PageHeading, Loading, ErrorState, Empty, PriorityBadge } from '../components/ui';
import { CountChart } from '../components/Charts';
export function OverviewPage() {
  const { openTask, createTask } = useAppUI();
  const summary = useQuery({
    queryKey: ['summary', 'personal'],
    queryFn: () => api<Summary>('/reports/personal'),
  });
  const upcoming = useQuery({
    queryKey: ['tasks', 'upcoming'],
    queryFn: () => api<Page<Task>>('/tasks?scope=mine&openOnly=true&sort=dueDate&size=5'),
  });
  const activity = useQuery({
    queryKey: ['activity', 'personal'],
    queryFn: () => api<Page<Activity>>('/activity?size=5'),
  });
  return (
    <div className="page">
      <PageHeading
        title="Overview"
        description={
          summary.data
            ? `${summary.data.dueToday} tasks due today. ${summary.data.overdue ? `${summary.data.overdue} overdue tasks need a little attention.` : 'A good day to move things forward.'}`
            : 'A little perspective on your work.'
        }
        actions={
          <Button onClick={() => createTask()}>
            <Plus size={16} />
            New task
          </Button>
        }
      />
      {summary.isPending ? (
        <Loading rows={2} />
      ) : summary.isError ? (
        <ErrorState error={summary.error} retry={() => summary.refetch()} />
      ) : (
        <>
          <div className="metrics-strip">
            {[
              ['Assigned tasks', summary.data.total],
              ['In progress', summary.data.inProgress],
              ['Completed', summary.data.completed],
              ['Due this week', summary.data.dueThisWeek],
            ].map(([label, value]) => (
              <div key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
          <div className="dashboard-grid">
            <section className="panel">
              <div className="section-title">
                <h2>Work by status</h2>
                <span>{summary.data.total} tasks</span>
              </div>
              <CountChart
                title="Tasks by status"
                data={Object.entries(summary.data.byStatus).map(([key, value]) => ({
                  label: statusLabels[key as keyof typeof statusLabels],
                  value,
                }))}
              />
            </section>
            <section className="panel">
              <div className="section-title">
                <h2>Priority breakdown</h2>
                <span>All assigned work</span>
              </div>
              <CountChart
                title="Tasks by priority"
                data={Object.entries(summary.data.byPriority).map(([key, value]) => ({
                  label: priorityLabels[key as keyof typeof priorityLabels],
                  value,
                }))}
              />
            </section>
          </div>
        </>
      )}
      <div className="dashboard-grid">
        <section className="panel">
          <div className="section-title">
            <h2>On your radar</h2>
            <Link to="/my-tasks">
              View all <ArrowUpRight size={14} />
            </Link>
          </div>
          {upcoming.isPending ? (
            <Loading rows={3} />
          ) : upcoming.isError ? (
            <ErrorState error={upcoming.error} retry={() => upcoming.refetch()} />
          ) : upcoming.data.content.length ? (
            <div className="radar-list">
              {upcoming.data.content.map((t) => (
                <button key={t.id} onClick={() => openTask(t.id)}>
                  <span>
                    <strong>{t.title}</strong>
                    <small>{t.projectName}</small>
                  </span>
                  <PriorityBadge priority={t.priority} />
                  <span className={t.overdue ? 'overdue' : ''}>{date(t.dueDate)}</span>
                </button>
              ))}
            </div>
          ) : (
            <Empty title="A clear horizon" description="Assigned tasks will appear here." />
          )}
        </section>
        <section className="panel">
          <div className="section-title">
            <h2>Recent updates</h2>
            <span>Across your projects</span>
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
              title="The story starts here"
              description="Your team’s updates will appear as work moves forward."
            />
          )}
        </section>
      </div>
    </div>
  );
}
