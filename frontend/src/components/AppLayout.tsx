import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  CheckCheck,
  LayoutDashboard,
  Bell,
  CalendarDays,
  FolderKanban,
  Users,
  ChartNoAxesCombined,
  Search,
  Settings,
  PanelLeftClose,
  PanelLeftOpen,
  ChevronDown,
  Plus,
  LogOut,
  Shield,
  ScrollText,
  Menu,
  X,
  CheckSquare,
  ArrowUpRight,
} from 'lucide-react';
import { useAuth } from '../lib/auth';
import { api } from '../lib/api';
import { initials } from '../lib/format';
import type { Page, Project } from '../lib/types';
import { Avatar, Button, Confirm, useToast } from './ui';
import { TaskCreate, ProjectCreate } from './CreateDialogs';
import { TaskDrawer } from './TaskDrawer';

interface AppUI {
  openTask: (id: string) => void;
  createTask: (projectId?: string) => void;
  createProject: () => void;
}
const AppUIContext = createContext<AppUI | null>(null);
export const useAppUI = () => {
  const value = useContext(AppUIContext);
  if (!value) throw new Error('App layout missing');
  return value;
};
const mainNav = [
  { to: '/my-tasks', label: 'My Tasks', icon: CheckSquare },
  { to: '/overview', label: 'Overview', icon: LayoutDashboard },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/calendar', label: 'My Calendar', icon: CalendarDays },
  { to: '/completed', label: 'Completed Tasks', icon: CheckCheck },
];
export function AppLayout() {
  const { user, logout } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const [collapsed, setCollapsed] = useState(localStorage.getItem('tf-sidebar') === 'collapsed'),
    [mobile, setMobile] = useState(false),
    [expanded, setExpanded] = useState(true),
    [newTask, setNewTask] = useState<{ projectId?: string } | null>(null),
    [newProject, setNewProject] = useState(false),
    [confirmLogout, setConfirmLogout] = useState(false);
  const projects = useQuery({
    queryKey: ['projects', 'navigation'],
    queryFn: () => api<Page<Project>>('/projects?size=100'),
  });
  const unread = useQuery({
    queryKey: ['notifications', 'count'],
    queryFn: () => api<{ count: number }>('/notifications/unread-count'),
    refetchInterval: 30000,
    retry: false,
  });
  const activeProject = projects.data?.content.find((p) =>
    location.pathname.startsWith(`/projects/${p.id}`),
  );
  const title =
    activeProject?.name ??
    [
      ...mainNav,
      { to: '/projects', label: 'Projects' },
      { to: '/team', label: 'Team' },
      { to: '/reports', label: 'Reports' },
      { to: '/search', label: 'Search' },
      { to: '/settings', label: 'Settings' },
      { to: '/admin/users', label: 'User management' },
      { to: '/admin/audit', label: 'Audit logs' },
    ].find((n) => location.pathname === n.to)?.label ??
    'Workspace';
  useEffect(() => {
    setMobile(false);
    document.title = `${title} · TaskFlow`;
  }, [location.pathname, title]);
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
        event.preventDefault();
        navigate('/search');
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [navigate]);
  if (!user) return null;
  function closeTask() {
    setParams((previous) => {
      previous.delete('task');
      return previous;
    });
  }
  const openTask = (id: string) =>
    setParams((previous) => {
      previous.set('task', id);
      return previous;
    });
  return (
    <AppUIContext.Provider
      value={{
        openTask,
        createTask: (projectId) => setNewTask({ projectId }),
        createProject: () => setNewProject(true),
      }}
    >
      <div
        className={`app-layout ${collapsed ? 'is-collapsed' : ''} ${mobile ? 'mobile-menu-open' : ''}`}
      >
        {mobile && (
          <button
            className="mobile-nav-backdrop"
            aria-label="Close navigation"
            onClick={() => setMobile(false)}
          />
        )}
        <aside className="sidebar" aria-label="Main navigation">
          <div className="brand-row">
            <Link className="brand" to="/my-tasks">
              <img src="/favicon.svg" alt="" />
              <span>
                TaskFlow<span className="brand-period">.</span>
              </span>
            </Link>
            <button
              className="icon-button mobile-close"
              aria-label="Close navigation"
              onClick={() => setMobile(false)}
            >
              <X size={18} />
            </button>
          </div>
          <div className="workspace-identity">
            <div className="workspace-monogram">W</div>
            <div>
              <strong>My workspace</strong>
              <span>{user.systemRole === 'ADMIN' ? 'Administration' : 'Your team, connected'}</span>
            </div>
          </div>
          <nav>
            <div className="nav-group">
              {mainNav.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
                  title={item.label}
                >
                  <item.icon size={18} />
                  <span>{item.label}</span>
                  {item.to === '/notifications' && !!unread.data?.count && (
                    <span className="nav-count">{unread.data.count}</span>
                  )}
                </NavLink>
              ))}
            </div>
            <div className="nav-section-heading">
              <button onClick={() => setExpanded(!expanded)} aria-expanded={expanded}>
                <ChevronDown size={13} className={expanded ? '' : 'rotated'} />
                <span>Projects</span>
              </button>
              <button aria-label="Create project" onClick={() => setNewProject(true)}>
                <Plus size={15} />
              </button>
            </div>
            <div className="project-nav">
              {expanded &&
                projects.data?.content
                  .filter((p) => p.status !== 'ARCHIVED')
                  .slice(0, 6)
                  .map((p) => (
                    <NavLink
                      key={p.id}
                      to={`/projects/${p.id}/overview`}
                      className={() => `nav-item ${activeProject?.id === p.id ? 'active' : ''}`}
                      title={p.name}
                    >
                      <span className="project-dot" />
                      <span>{p.name}</span>
                    </NavLink>
                  ))}
              <NavLink to="/projects" end className="nav-item" title="All projects">
                <FolderKanban size={18} />
                <span>All projects</span>
              </NavLink>
            </div>
            <div className="nav-group secondary-nav">
              <NavLink to="/team" className="nav-item" title="Team">
                <Users size={18} />
                <span>Team</span>
              </NavLink>
              <NavLink to="/reports" className="nav-item" title="Reports">
                <ChartNoAxesCombined size={18} />
                <span>Reports</span>
              </NavLink>
            </div>
            {user.systemRole === 'ADMIN' && (
              <div className="nav-group admin-nav">
                <NavLink className="nav-item" to="/admin/users" title="User management">
                  <Shield size={18} />
                  <span>User management</span>
                </NavLink>
                <NavLink className="nav-item" to="/admin/audit" title="Audit logs">
                  <ScrollText size={18} />
                  <span>Audit logs</span>
                </NavLink>
              </div>
            )}
          </nav>
          <div className="sidebar-bottom">
            <NavLink to="/search" className="nav-item" title="Search">
              <Search size={18} />
              <span>Search</span>
              <kbd>Ctrl K</kbd>
            </NavLink>
            <NavLink to="/settings" className="nav-item" title="Settings">
              <Settings size={18} />
              <span>Settings</span>
            </NavLink>
            <div className="user-row">
              <Link to="/settings" title={user.fullName}>
                <Avatar person={user} size="medium" />
                <div>
                  <strong>{user.fullName}</strong>
                  <span>{user.jobTitle || 'Team member'}</span>
                </div>
              </Link>
              <button
                className="icon-button"
                aria-label="Sign out"
                onClick={() => setConfirmLogout(true)}
              >
                <LogOut size={16} />
              </button>
            </div>
          </div>
        </aside>
        <div className="app-body">
          <header className="topbar">
            <div className="breadcrumbs">
              <button
                className="icon-button sidebar-toggle"
                aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
                onClick={() => {
                  setCollapsed(!collapsed);
                  localStorage.setItem('tf-sidebar', collapsed ? 'expanded' : 'collapsed');
                }}
              >
                {collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
              </button>
              <button
                className="icon-button mobile-menu-toggle"
                aria-label="Open navigation"
                onClick={() => setMobile(true)}
              >
                <Menu size={20} />
              </button>
              <span className="breadcrumb-workspace">
                Workspace<span className="breadcrumb-slash">/</span>
              </span>
              <span>{title}</span>
            </div>
            <div className="topbar-actions">
              <Link to="/search" className="topbar-search">
                <Search size={15} />
                <span>Search anything</span>
                <kbd>Ctrl K</kbd>
              </Link>
              <Link
                className="icon-button notification-button"
                to="/notifications"
                aria-label="Notifications"
              >
                <Bell size={18} />
                {!!unread.data?.count && <span />}
              </Link>
              <Link
                to="/settings"
                aria-label={`${initials(user.fullName)} — ${user.fullName}, profile settings`}
              >
                <Avatar person={user} />
              </Link>
            </div>
          </header>
          <main id="main-content" className="main-canvas">
            <Outlet />
          </main>
          <footer className="app-footer">
            <span>A little clarity. A better day.</span>
            <a href="http://localhost:8080/docs" target="_blank" rel="noreferrer">
              API documentation <ArrowUpRight size={12} />
            </a>
          </footer>
        </div>
      </div>
      {newTask && (
        <TaskCreate open onClose={() => setNewTask(null)} projectId={newTask.projectId} />
      )}
      <ProjectCreate open={newProject} onClose={() => setNewProject(false)} />
      {params.get('task') && <TaskDrawer id={params.get('task')!} onClose={closeTask} />}
      <Confirm
        open={confirmLogout}
        onClose={() => setConfirmLogout(false)}
        title="Sign out of TaskFlow?"
        description="Your saved work will be here when you return."
        onConfirm={() => logout().catch((e) => toast(e.message, 'error'))}
      />
    </AppUIContext.Provider>
  );
}
