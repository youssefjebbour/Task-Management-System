import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { api } from '../lib/api';
import type { Page, Task } from '../lib/types';
import { dateInput, date, statusLabels } from '../lib/format';
import { useAppUI } from '../components/AppLayout';
import { Button, PageHeading, Loading, ErrorState, Empty, PriorityBadge } from '../components/ui';
export function CalendarPage() {
  const now = new Date();
  const [month, setMonth] = useState(new Date(now.getFullYear(), now.getMonth(), 1)),
    [selected, setSelected] = useState(dateInput(now));
  const { openTask, createTask } = useAppUI();
  const first = new Date(month.getFullYear(), month.getMonth(), 1),
    last = new Date(month.getFullYear(), month.getMonth() + 1, 0);
  const from = dateInput(first),
    to = dateInput(last);
  const query = useInfiniteQuery({
    queryKey: ['tasks', 'calendar', from, to],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api<Page<Task>>(
        `/tasks?scope=mine&dueFrom=${from}&dueTo=${to}&size=100&page=${pageParam}&sort=dueDate`,
      ),
    getNextPageParam: (p) => (p.page + 1 < p.totalPages ? p.page + 1 : undefined),
  });
  const tasks = query.data?.pages.flatMap((p) => p.content) ?? [];
  const days = Array.from(
    { length: Math.ceil((first.getDay() + last.getDate()) / 7) * 7 },
    (_, i) => new Date(month.getFullYear(), month.getMonth(), i - first.getDay() + 1),
  );
  const selectedTasks = tasks.filter((t) => t.dueDate === selected);
  function move(delta: number) {
    const next = new Date(month.getFullYear(), month.getMonth() + delta, 1);
    setMonth(next);
    setSelected(dateInput(next));
  }
  return (
    <div className="page">
      <PageHeading
        title="My Calendar"
        description="A little space to see what’s coming."
        actions={
          <Button onClick={() => createTask()}>
            <Plus size={16} />
            New task
          </Button>
        }
      />
      <div className="calendar-toolbar">
        <h2>{month.toLocaleDateString('en', { month: 'long', year: 'numeric' })}</h2>
        <div>
          <Button
            onClick={() => {
              setMonth(new Date(now.getFullYear(), now.getMonth(), 1));
              setSelected(dateInput(now));
            }}
          >
            Today
          </Button>
          <Button aria-label="Previous month" onClick={() => move(-1)}>
            <ChevronLeft size={16} />
          </Button>
          <Button aria-label="Next month" onClick={() => move(1)}>
            <ChevronRight size={16} />
          </Button>
        </div>
      </div>
      {query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : (
        <>
          <div className="calendar-grid">
            <div className="calendar-weekdays">
              {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((d) => (
                <span key={d}>{d}</span>
              ))}
            </div>
            <div className="calendar-days">
              {days.map((day) => {
                const key = dateInput(day),
                  dayTasks = tasks.filter((t) => t.dueDate === key);
                return (
                  <div
                    key={key}
                    className={`calendar-day ${day.getMonth() !== month.getMonth() ? 'other-month' : ''} ${key === selected ? 'selected' : ''}`}
                  >
                    <button
                      className={`day-number ${key === dateInput(now) ? 'today' : ''}`}
                      aria-label={day.toLocaleDateString('en', { dateStyle: 'full' })}
                      aria-pressed={key === selected}
                      onClick={() => setSelected(key)}
                    >
                      {day.getDate()}
                    </button>
                    <div className="calendar-task-previews">
                      {dayTasks.slice(0, 3).map((t) => (
                        <button
                          key={t.id}
                          title={t.title}
                          className={t.status === 'COMPLETED' ? 'completed' : ''}
                          onClick={() => openTask(t.id)}
                        >
                          <span className={`status-dot dot-${t.status.toLowerCase()}`} />
                          {t.title}
                        </button>
                      ))}
                      {dayTasks.length > 3 && (
                        <button className="calendar-more" onClick={() => setSelected(key)}>
                          +{dayTasks.length - 3} more
                        </button>
                      )}
                    </div>
                    {dayTasks.length > 0 && (
                      <button className="calendar-mobile-count" onClick={() => setSelected(key)}>
                        {dayTasks.length} <span className="sr-only">tasks</span>
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
          {query.isPending && <Loading rows={1} />}
          <div className="calendar-agenda">
            <div className="section-title">
              <h2>{date(selected, { weekday: 'long', month: 'long', day: 'numeric' })}</h2>
              <span>{selectedTasks.length} tasks</span>
            </div>
            {selectedTasks.length ? (
              <div className="agenda-list">
                {selectedTasks.map((t) => (
                  <button key={t.id} onClick={() => openTask(t.id)}>
                    <span className={`status-dot dot-${t.status.toLowerCase()}`} />
                    <div>
                      <strong>{t.title}</strong>
                      <span>
                        {t.projectName} · {statusLabels[t.status]}
                      </span>
                    </div>
                    <PriorityBadge priority={t.priority} />
                  </button>
                ))}
              </div>
            ) : (
              <p className="muted">Nothing due on this day.</p>
            )}
          </div>
          {query.hasNextPage && (
            <div className="load-more">
              <span>
                {tasks.length} of {query.data?.pages[0].totalElements} scheduled tasks loaded.
              </span>
              <Button busy={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>
                Load more tasks
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
