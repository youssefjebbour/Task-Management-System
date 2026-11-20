import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { Project, Page, Summary } from '../lib/types';
import { statusLabels, priorityLabels } from '../lib/format';
import { PageHeading, ErrorState, Loading } from '../components/ui';
import { CountChart } from '../components/Charts';
export function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const selected = params.get('project') || '';
  const projects = useQuery({
    queryKey: ['projects', 'navigation'],
    queryFn: () => api<Page<Project>>('/projects?size=100'),
  });
  const query = useQuery({
    queryKey: ['summary', 'reports', selected],
    queryFn: () => api<Summary>(selected ? `/reports/projects/${selected}` : '/reports/personal'),
  });
  return (
    <div className="page">
      <PageHeading
        title="Reports"
        description="A clearer picture of progress and responsibility."
      />
      <div className="report-scope">
        <label>
          Report scope
          <select
            value={selected}
            onChange={(e) => setParams(e.target.value ? { project: e.target.value } : {})}
          >
            <option value="">My assigned work</option>
            {projects.data?.content
              .filter((p) => p.canReport)
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
          </select>
        </label>
        <span>Based on current task records</span>
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : (
        <>
          <div className="report-lead">
            <div>
              <span>Completion</span>
              <strong>
                {query.data.completionPercent}
                <small>%</small>
              </strong>
              <p>
                {query.data.completed} of {query.data.total} tasks completed
              </p>
            </div>
            <div className="report-lead-side">
              <div>
                <span>Open tasks</span>
                <strong>{query.data.pending + query.data.inProgress}</strong>
              </div>
              <div>
                <span>Overdue</span>
                <strong className={query.data.overdue ? 'overdue' : ''}>
                  {query.data.overdue}
                </strong>
              </div>
              <div>
                <span>Due this week</span>
                <strong>{query.data.dueThisWeek}</strong>
              </div>
            </div>
          </div>
          <div className="dashboard-grid">
            <section className="panel">
              <div className="section-title">
                <h2>Task distribution</h2>
                <span>By status</span>
              </div>
              <CountChart
                title="Task distribution"
                data={Object.entries(query.data.byStatus).map(([k, value]) => ({
                  label: statusLabels[k as keyof typeof statusLabels],
                  value,
                }))}
              />
            </section>
            <section className="panel">
              <div className="section-title">
                <h2>Where focus is needed</h2>
                <span>By priority</span>
              </div>
              <CountChart
                title="Priority distribution"
                data={Object.entries(query.data.byPriority).map(([k, value]) => ({
                  label: priorityLabels[k as keyof typeof priorityLabels],
                  value,
                }))}
              />
            </section>
          </div>
          {selected && (
            <section className="workload-panel">
              <div className="section-title">
                <h2>Workload by member</h2>
                <span>Team leads see tasks in their teams</span>
              </div>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Team member</th>
                    <th className="num">Assigned tasks</th>
                    <th className="num">Completed</th>
                    <th className="num">Remaining</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.byMember.map((m) => (
                    <tr key={m.id}>
                      <td>{m.name}</td>
                      <td className="num">{m.total}</td>
                      <td className="num">{m.completed}</td>
                      <td className="num">{m.total - m.completed}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
        </>
      )}
    </div>
  );
}
