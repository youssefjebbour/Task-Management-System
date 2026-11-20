import type { Session } from './types';
let accessToken: string | null = null;
let refreshPromise: Promise<Session> | null = null;
let sessionListener: (session: Session | null) => void = () => {};
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: Record<string, string> = {},
  ) {
    super(message);
  }
}
export function setSessionListener(listener: typeof sessionListener) {
  sessionListener = listener;
}
export function setAccessToken(token: string | null) {
  accessToken = token;
}
async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let body;
    try {
      body = await response.json();
    } catch {
      body = { message: 'The server could not complete your request.' };
    }
    throw new ApiError(response.status, body.message || 'Request failed', body.errors || {});
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export async function refreshSession(): Promise<Session> {
  if (!refreshPromise)
    refreshPromise = (async () => {
      const rotate = () =>
        fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' }).then(
          decode<Session>,
        );
      // Serialize cookie rotation across tabs; each request reads the latest browser cookie.
      try {
        const session = await (navigator.locks
          ? navigator.locks.request('taskflow-refresh', rotate)
          : rotate());
        accessToken = session.accessToken;
        sessionListener(session);
        return session;
      } catch (error) {
        accessToken = null;
        sessionListener(null);
        throw error;
      } finally {
        refreshPromise = null;
      }
    })();
  return refreshPromise;
}
export async function api<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData) && options.body)
    headers.set('Content-Type', 'application/json');
  if (accessToken && !path.startsWith('/auth/'))
    headers.set('Authorization', `Bearer ${accessToken}`);
  const response = await fetch(`/api/v1${path}`, { ...options, headers, credentials: 'include' });
  if (response.status === 401 && retry && !path.startsWith('/auth/')) {
    await refreshSession();
    return api<T>(path, options, false);
  }
  return decode<T>(response);
}
export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
export const put = <T>(path: string, body: unknown) =>
  api<T>(path, { method: 'PUT', body: JSON.stringify(body) });
export const patch = <T>(path: string, body: unknown) =>
  api<T>(path, { method: 'PATCH', body: JSON.stringify(body) });
export const del = (path: string) => api<void>(path, { method: 'DELETE' });
export function queryString(values: Record<string, unknown>) {
  const p = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') p.set(key, String(value));
  });
  return p.toString();
}
export async function download(path: string, filename: string, retry = true) {
  const response = await fetch(`/api/v1${path}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
    credentials: 'include',
  });
  if (response.status === 401 && retry) {
    await refreshSession();
    return download(path, filename, false);
  }
  if (!response.ok) throw new ApiError(response.status, 'Could not download this attachment');
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
