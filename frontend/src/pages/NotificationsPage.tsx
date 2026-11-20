import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck, Mail, MailOpen, ArrowUpRight } from 'lucide-react';
import { api, patch, post } from '../lib/api';
import type { Notice, Page } from '../lib/types';
import { relative } from '../lib/format';
import { useAppUI } from '../components/AppLayout';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  PageHeading,
  Empty,
  ErrorState,
  Loading,
  Pagination,
  useToast,
} from '../components/ui';
export function NotificationsPage() {
  const client = useQueryClient(),
    toast = useToast(),
    navigate = useNavigate();
  const { openTask } = useAppUI();
  const [unread, setUnread] = useState(false),
    [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['notifications', 'list', unread, page],
    queryFn: () => api<Page<Notice>>(`/notifications?unread=${unread}&page=${page}&size=15`),
  });
  const mark = useMutation({
    mutationFn: (n: Notice) => patch(`/notifications/${n.id}`, { read: !n.readAt }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
    onError: (e) => toast(e.message, 'error'),
  });
  const markAll = useMutation({
    mutationFn: () => post('/notifications/read-all'),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['notifications'] });
      toast('All notifications marked as read');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  function open(n: Notice) {
    if (!n.readAt) mark.mutate(n);
    if (n.taskId) openTask(n.taskId);
    else if (n.projectId) navigate(`/projects/${n.projectId}/overview`);
  }
  return (
    <div className="page">
      <PageHeading
        title="Notifications"
        description="The updates that keep everyone connected."
        actions={
          <Button busy={markAll.isPending} onClick={() => markAll.mutate()}>
            <CheckCheck size={16} />
            Mark all as read
          </Button>
        }
      />
      <div className="tabs">
        <button
          className={!unread ? 'active' : ''}
          onClick={() => {
            setUnread(false);
            setPage(0);
          }}
        >
          All updates
        </button>
        <button
          className={unread ? 'active' : ''}
          onClick={() => {
            setUnread(true);
            setPage(0);
          }}
        >
          Unread
        </button>
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : query.data.content.length ? (
        <>
          <div className="notification-list">
            {query.data.content.map((n) => (
              <article key={n.id} className={`notification-row ${!n.readAt ? 'unread' : ''}`}>
                <div className="notification-indicator">
                  {!n.readAt ? <span /> : <CheckCheck size={14} />}
                </div>
                <button className="notification-content" onClick={() => open(n)}>
                  <strong>{n.title}</strong>
                  <p>{n.message}</p>
                  <span>{relative(n.createdAt)}</span>
                </button>
                <button
                  className="icon-button"
                  aria-label={n.readAt ? 'Mark as unread' : 'Mark as read'}
                  onClick={() => mark.mutate(n)}
                >
                  {n.readAt ? <Mail size={17} /> : <MailOpen size={17} />}
                </button>
              </article>
            ))}
          </div>
          <Pagination data={query.data} onPage={setPage} />
        </>
      ) : (
        <Empty
          title="You’re all caught up"
          description={
            unread
              ? 'No unread updates. Enjoy a little breathing room.'
              : 'Assignments, comments, and project updates will appear here.'
          }
        />
      )}
    </div>
  );
}
