import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Search, Shield, UserCheck, UserX } from 'lucide-react';
import { api, patch } from '../lib/api';
import { useAuth } from '../lib/auth';
import type { User, Activity, Page } from '../lib/types';
import { date, relative } from '../lib/format';
import {
  PageHeading,
  Button,
  Avatar,
  Loading,
  ErrorState,
  Pagination,
  Confirm,
  Empty,
  useToast,
} from '../components/ui';
export function AdminUsersPage() {
  const { user } = useAuth();
  const client = useQueryClient(),
    toast = useToast();
  const [q, setQ] = useState(''),
    [page, setPage] = useState(0),
    [action, setAction] = useState<{ user: User; active: boolean; systemRole: string } | null>(
      null,
    );
  const query = useQuery({
    queryKey: ['admin', 'users', q, page],
    queryFn: () => api<Page<User>>(`/users?q=${encodeURIComponent(q)}&page=${page}&size=15`),
    enabled: user?.systemRole === 'ADMIN',
  });
  const change = useMutation({
    mutationFn: () =>
      patch(`/users/${action!.user.id}`, {
        active: action!.active,
        systemRole: action!.systemRole,
      }),
    onSuccess: () => {
      client.invalidateQueries();
      setAction(null);
      toast('Account updated');
    },
    onError: (e) => toast(e.message, 'error'),
  });
  if (user?.systemRole !== 'ADMIN') return <Navigate to="/my-tasks" replace />;
  return (
    <div className="page">
      <PageHeading
        title="User management"
        description="Manage platform access and account status."
      />
      <div className="admin-search search-field">
        <Search size={16} />
        <input
          aria-label="Search users"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          placeholder="Find a name or email…"
        />
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : query.data.content.length ? (
        <>
          <div className="admin-user-list">
            {query.data.content.map((u) => (
              <div key={u.id} className="admin-user-row">
                <Avatar person={u} size="medium" />
                <div className="member-name">
                  <strong>
                    {u.fullName}
                    {u.id === user.id ? ' (you)' : ''}
                  </strong>
                  <span>{u.email}</span>
                </div>
                <span className={`account-status ${u.active ? '' : 'inactive'}`}>
                  {u.active ? 'Active' : 'Deactivated'}
                </span>
                <select
                  aria-label={`System role for ${u.fullName}`}
                  value={u.systemRole}
                  disabled={u.id === user.id}
                  onChange={(e) =>
                    setAction({ user: u, active: u.active, systemRole: e.target.value })
                  }
                >
                  <option value="USER">User</option>
                  <option value="ADMIN">Admin</option>
                </select>
                <Button
                  variant="ghost"
                  disabled={u.id === user.id}
                  onClick={() =>
                    setAction({ user: u, active: !u.active, systemRole: u.systemRole })
                  }
                >
                  {u.active ? <UserX size={15} /> : <UserCheck size={15} />}
                  <span>{u.active ? 'Deactivate' : 'Activate'}</span>
                </Button>
              </div>
            ))}
          </div>
          <Pagination data={query.data} onPage={setPage} />
        </>
      ) : (
        <Empty title="No matching accounts" description="Try a different name or email." />
      )}
      <Confirm
        open={!!action}
        onClose={() => setAction(null)}
        title="Update this account?"
        description={`${action?.user.fullName} will be ${action?.active ? 'active' : 'deactivated'} with the ${action?.systemRole.toLowerCase()} role. Existing sessions will be invalidated.`}
        onConfirm={() => change.mutate()}
        busy={change.isPending}
      />
    </div>
  );
}
export function AuditPage() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['audit', page],
    queryFn: () => api<Page<Activity>>(`/activity?audit=true&page=${page}&size=25`),
    enabled: user?.systemRole === 'ADMIN',
  });
  if (user?.systemRole !== 'ADMIN') return <Navigate to="/my-tasks" replace />;
  return (
    <div className="page">
      <PageHeading
        title="System audit logs"
        description="A durable record of important changes across the platform."
      />
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : (
        <>
          <div className="audit-table-wrap">
            <table className="data-table audit-table">
              <thead>
                <tr>
                  <th>Event</th>
                  <th>Details</th>
                  <th>Actor</th>
                  <th className="num">When</th>
                </tr>
              </thead>
              <tbody>
                {query.data.content.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <span className="audit-action">
                        {a.action.toLowerCase().replaceAll('_', ' ')}
                      </span>
                    </td>
                    <td>{a.description}</td>
                    <td>{a.actorName}</td>
                    <td className="num" title={new Date(a.createdAt).toLocaleString()}>
                      {relative(a.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination data={query.data} onPage={setPage} />
        </>
      )}
    </div>
  );
}
