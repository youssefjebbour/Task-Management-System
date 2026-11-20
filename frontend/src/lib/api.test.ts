import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, refreshSession, setAccessToken, setSessionListener } from './api';

const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const session = { accessToken: 'rotated-access', expiresIn: 600, user: { id: 'user-1' } };
beforeEach(() => {
  setAccessToken(null);
  setSessionListener(() => {});
  vi.stubGlobal('navigator', {});
});
afterEach(() => vi.unstubAllGlobals());
describe('authenticated API client', () => {
  it('rotates an expired access token and retries the protected operation once', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(json(session))
      .mockResolvedValueOnce(json({ title: 'Persisted task' }));
    vi.stubGlobal('fetch', fetch);
    setAccessToken('expired');
    expect(await api('/tasks/task-1')).toEqual({ title: 'Persisted task' });
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(fetch.mock.calls[2][1].headers.get('Authorization')).toBe('Bearer rotated-access');
    expect(fetch.mock.calls[1][0]).toBe('/api/v1/auth/refresh');
  });
  it('shares an in-flight rotation within a tab and uses the cross-tab lock', async () => {
    const lock = vi.fn(async (_name: string, work: () => Promise<unknown>) => work());
    const fetch = vi.fn().mockResolvedValue(json(session));
    vi.stubGlobal('fetch', fetch);
    vi.stubGlobal('navigator', { locks: { request: lock } });
    await Promise.all([refreshSession(), refreshSession(), refreshSession()]);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(lock).toHaveBeenCalledTimes(1);
  });
  it('preserves field validation errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          json({ message: 'Check fields', errors: { title: 'Must not be blank' } }, 400),
        ),
    );
    await expect(api('/tasks')).rejects.toMatchObject({
      status: 400,
      errors: { title: 'Must not be blank' },
    });
  });
  it('never loops on a rejected refresh and clears the session', async () => {
    const fetch = vi.fn().mockResolvedValue(json({ message: 'Expired' }, 401)),
      listener = vi.fn();
    vi.stubGlobal('fetch', fetch);
    setSessionListener(listener);
    await expect(api('/tasks')).rejects.toBeInstanceOf(ApiError);
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(listener).toHaveBeenCalledWith(null);
  });
  it('lets the browser set the multipart boundary', async () => {
    const fetch = vi.fn().mockResolvedValue(json({ id: 'attachment' }));
    vi.stubGlobal('fetch', fetch);
    const body = new FormData();
    body.append('file', new Blob(['notes']), 'notes.txt');
    await api('/tasks/id/attachments', { method: 'POST', body });
    expect(fetch.mock.calls[0][1].headers.has('Content-Type')).toBe(false);
    expect(fetch.mock.calls[0][1].body).toBe(body);
  });
});
