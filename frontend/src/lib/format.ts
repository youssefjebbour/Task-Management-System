export const statusLabels = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};
export const priorityLabels = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' };
export const roleLabels = {
  OWNER: 'Owner',
  MANAGER: 'Manager',
  TEAM_LEAD: 'Team lead',
  MEMBER: 'Member',
  VIEWER: 'Viewer',
};
export const projectStatusLabels = {
  PLANNING: 'Planning',
  ACTIVE: 'Active',
  ON_HOLD: 'On hold',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
};
export function date(value: string | null | undefined, options?: Intl.DateTimeFormatOptions) {
  if (!value) return 'No due date';
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', ...options }).format(
    new Date(value.length === 10 ? `${value}T12:00:00` : value),
  );
}
export function relative(value: string) {
  const seconds = Math.floor((Date.now() - new Date(value).getTime()) / 1000);
  if (seconds < 60) return 'Just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  if (seconds < 604800) return `${Math.floor(seconds / 86400)}d ago`;
  return date(value);
}
export function dateInput(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}
export function initials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((s) => s[0])
    .join('')
    .toUpperCase();
}
