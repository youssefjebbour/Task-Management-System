export type SystemRole = 'ADMIN' | 'USER';
export type ProjectRole = 'OWNER' | 'MANAGER' | 'TEAM_LEAD' | 'MEMBER' | 'VIEWER';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED';
export interface Person {
  id: string;
  fullName: string;
  username: string;
  jobTitle: string | null;
  active: boolean;
}
export interface User extends Person {
  email: string;
  systemRole: SystemRole;
  createdAt: string;
}
export interface Session {
  accessToken: string;
  expiresIn: number;
  user: User;
}
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Project {
  id: string;
  name: string;
  projectKey: string;
  description: string | null;
  status: ProjectStatus;
  owner: Person;
  startDate: string | null;
  deadline: string | null;
  membersCanCreate: boolean;
  membersCanSelfAssign: boolean;
  membersCanAssign: boolean;
  blockIncompleteDependencies: boolean;
  role: ProjectRole | null;
  canManage: boolean;
  canCreateTask: boolean;
  canTransfer: boolean;
  canReport: boolean;
  totalTasks: number;
  completedTasks: number;
  version: number;
  createdAt: string;
}
export interface Member {
  id: string;
  user: Person;
  role: ProjectRole;
  joinedAt: string;
}
export interface Team {
  id: string;
  projectId: string;
  name: string;
  members: Person[];
}
export interface Task {
  id: string;
  key: string;
  title: string;
  description: string | null;
  projectId: string;
  projectName: string;
  projectKey: string;
  creator: Person;
  assignee: Person | null;
  teamId: string | null;
  teamName: string | null;
  status: TaskStatus;
  priority: Priority;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  overdue: boolean;
  version: number;
  permissions: {
    edit: boolean;
    assign: boolean;
    selfAssign: boolean;
    delete: boolean;
    comment: boolean;
  };
}
export interface Summary {
  total: number;
  completed: number;
  pending: number;
  inProgress: number;
  cancelled: number;
  overdue: number;
  dueToday: number;
  dueThisWeek: number;
  completionPercent: number;
  byStatus: Record<TaskStatus, number>;
  byPriority: Record<Priority, number>;
  byMember: { id: string; name: string; total: number; completed: number }[];
}
export interface Activity {
  id: string;
  projectId: string | null;
  taskId: string | null;
  actorName: string;
  action: string;
  description: string;
  createdAt: string;
}
export interface Notice {
  id: string;
  projectId: string | null;
  taskId: string | null;
  title: string;
  message: string;
  readAt: string | null;
  createdAt: string;
}
export interface Comment {
  id: string;
  author: Person;
  body: string;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canDelete: boolean;
}
export interface Label {
  id: string;
  name: string;
  color: string;
}
export interface Attachment {
  id: string;
  filename: string;
  contentType: string;
  size: number;
  uploader: Person;
  createdAt: string;
  canDelete: boolean;
}
export interface Dependency {
  id: string;
  taskId: string;
  key: string;
  title: string;
  status: TaskStatus;
}
export interface Recurrence {
  id: string;
  frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  nextDate: string;
  active: boolean;
}
