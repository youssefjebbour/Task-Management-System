import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './lib/auth';
import { ToastProvider, Loading, Empty } from './components/ui';
import { AppLayout } from './components/AppLayout';
import { AuthPage } from './pages/AuthPage';
import { TasksPage } from './pages/TasksPage';
import { OverviewPage } from './pages/OverviewPage';
import { ProjectsPage, ProjectLayout, ProjectOverview } from './pages/ProjectsPage';
import { BoardPage } from './pages/BoardPage';
import { MembersPage } from './pages/MembersPage';
import { ProjectSettings } from './pages/ProjectSettings';
import { SettingsPage, applyTheme } from './pages/SettingsPage';
import './styles.css';
import './workspace.css';
import { NotificationsPage } from './pages/NotificationsPage';
import { CalendarPage } from './pages/CalendarPage';
import { ReportsPage } from './pages/ReportsPage';
import { TeamPage } from './pages/TeamPage';
import { AdminUsersPage, AuditPage } from './pages/AdminPages';
applyTheme(localStorage.getItem('tf-theme') || 'light');
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
  if (localStorage.getItem('tf-theme') === 'system') applyTheme('system');
});
const client = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 15000, retry: 1, refetchOnWindowFocus: false },
    mutations: { retry: 0 },
  },
});
function Private() {
  const { user, loading } = useAuth();
  if (loading)
    return (
      <div className="page">
        <Loading />
      </div>
    );
  return user ? <AppLayout /> : <Navigate to="/login" replace />;
}
function ProjectTasks() {
  const { id } = useParams();
  return <TasksPage mode="project" projectId={id} />;
}
function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="/forgot-password" element={<AuthPage mode="forgot" />} />
      <Route path="/reset-password" element={<AuthPage mode="reset" />} />
      <Route element={<Private />}>
        <Route index element={<Navigate to="/my-tasks" replace />} />
        <Route path="my-tasks" element={<TasksPage />} />
        <Route path="completed" element={<TasksPage mode="completed" />} />
        <Route path="search" element={<TasksPage mode="search" />} />
        <Route path="overview" element={<OverviewPage />} />
        <Route path="projects" element={<ProjectsPage />} />
        <Route path="projects/:id" element={<ProjectLayout />}>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<ProjectOverview />} />
          <Route path="board" element={<BoardPage />} />
          <Route path="list" element={<ProjectTasks />} />
          <Route path="members" element={<MembersPage />} />
          <Route path="settings" element={<ProjectSettings />} />
        </Route>
        <Route path="settings" element={<SettingsPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="team" element={<TeamPage />} />
        <Route path="admin/users" element={<AdminUsersPage />} />
        <Route path="admin/audit" element={<AuditPage />} />
        <Route
          path="*"
          element={
            <div className="page">
              <Empty
                title="Page not found"
                description="Choose a page from the sidebar to continue."
              />
            </div>
          }
        />
      </Route>
    </Routes>
  );
}
ReactDOM.createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={client}>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </QueryClientProvider>,
);
