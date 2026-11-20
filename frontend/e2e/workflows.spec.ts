import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import AxeBuilder from '@axe-core/playwright';
const envFile = path.resolve(process.cwd(), '../.env');
const file = fs.existsSync(envFile) ? fs.readFileSync(envFile, 'utf8') : '';
const password =
  process.env.SEED_PASSWORD ||
  file
    .split(/\r?\n/)
    .find((l) => l.startsWith('SEED_PASSWORD='))
    ?.slice(14);
if (!password)
  throw new Error('Set SEED_PASSWORD or configure the local .env before browser tests.');
async function login(page: Page, email = 'maya@taskflow.local') {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password!);
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/auth/login') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const session = await (await response).json();
  await expect(page.getByRole('heading', { name: 'My Tasks', exact: true })).toBeVisible();
  return session.accessToken as string;
}
test('key screens have no automated WCAG A or AA violations', async ({ page }) => {
  await page.goto('/login');
  const inspect = async () => {
    await page.waitForLoadState('networkidle');
    const result = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(
      result.violations.map((v) => ({
        id: v.id,
        impact: v.impact,
        nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
      })),
    ).toEqual([]);
  };
  await inspect();
  await login(page);
  for (const route of ['/my-tasks', '/overview', '/settings']) {
    await page.goto(route);
    await expect(page.locator('[aria-label="Loading"]')).toHaveCount(0);
    await inspect();
  }
  await page.getByRole('radio', { name: 'Dark', exact: true }).click();
  await page.getByRole('radio', { name: 'Dark', exact: true }).press('ArrowRight');
  await expect(page.getByRole('radio', { name: 'System', exact: true })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await page.getByRole('radio', { name: 'System', exact: true }).press('ArrowLeft');
  await expect(page.getByRole('radio', { name: 'Dark', exact: true })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await page.goto('/my-tasks');
  await inspect();
  await page.setViewportSize({ width: 390, height: 900 });
  await inspect();
});
test('task, comment, label and board changes persist through real API requests', async ({
  page,
  request,
}) => {
  const token = await login(page);
  const headers = { Authorization: `Bearer ${token}` };
  let projectId = '';
  try {
    await page
      .locator('aside')
      .getByRole('button', { name: 'Create project', exact: true })
      .click();
    const dialog = page.getByRole('dialog', { name: 'Create a project', exact: true });
    await dialog.getByLabel('Project name').fill('Browser workflow verification');
    await dialog.getByLabel('Project key').fill('E' + Date.now().toString(36).slice(-8));
    await dialog
      .getByLabel('Description')
      .fill('Temporary project created by the automated browser test.');
    await dialog.getByRole('button', { name: 'Create project', exact: true }).click();
    await expect(
      page.getByRole('heading', { name: 'Browser workflow verification', exact: true }),
    ).toBeVisible();
    projectId = page.url().match(/projects\/([^/]+)/)![1];
    await page.getByRole('button', { name: 'New task', exact: true }).click();
    const create = page.getByRole('dialog', { name: 'Create a task', exact: true });
    await create.getByLabel('Task title').fill('Review release acceptance');
    await create.getByLabel('Description').fill('Verify the actual browser-to-database workflow.');
    await create.getByLabel('Assignee').selectOption({ label: 'Maya Chen' });
    await create.getByLabel('Priority').selectOption('HIGH');
    await create.getByRole('button', { name: 'Create task', exact: true }).click();
    await page.getByRole('link', { name: 'List', exact: true }).click();
    await page.getByRole('button', { name: 'Review release acceptance', exact: true }).click();
    await expect(
      page.getByRole('heading', { name: 'Review release acceptance', exact: true }),
    ).toBeVisible();
    await page
      .getByLabel('Add a comment', { exact: true })
      .fill('The real API has persisted this review note.');
    await page.getByRole('button', { name: 'Comment', exact: true }).click();
    await expect(
      page.getByText('The real API has persisted this review note.', { exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Create label', exact: true }).click();
    const labels = page.getByRole('dialog', { name: 'Create a project label' });
    await labels.getByLabel('Label name').fill('Release');
    await labels.getByRole('button', { name: 'Create label', exact: true }).click();
    await expect(page.locator('.task-label').filter({ hasText: 'Release' })).toBeVisible();
    await page.getByLabel('Upload attachment').setInputFiles({
      name: 'release-notes.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('Verified attachment workflow.'),
    });
    await expect(
      page.locator('.attachment-list').getByText('release-notes.txt', { exact: true }),
    ).toBeVisible();
    const downloaded = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download release-notes.txt', exact: true }).click();
    const fileDownload = await downloaded;
    expect(fileDownload.suggestedFilename()).toBe('release-notes.txt');
    const savedPath = await fileDownload.path();
    expect(fs.readFileSync(savedPath!, 'utf8')).toBe('Verified attachment workflow.');
    await page.getByLabel('Task status', { exact: true }).selectOption('IN_PROGRESS');
    await expect(page.getByLabel('Task status', { exact: true })).toHaveValue('IN_PROGRESS');
    await page.getByRole('button', { name: 'Close task details' }).click();
    await page.getByRole('link', { name: 'Board', exact: true }).click();
    const card = page.locator('.kanban-card').filter({ hasText: 'Review release acceptance' });
    await expect(card).toBeVisible();
    const handle = await card
      .getByRole('button', { name: 'Move Review release acceptance' })
      .boundingBox();
    const target = await page
      .locator('.kanban-column')
      .filter({ has: page.getByRole('heading', { name: 'To do 0', exact: true }) })
      .boundingBox();
    await page.mouse.move(handle!.x + handle!.width / 2, handle!.y + handle!.height / 2);
    await page.mouse.down();
    await page.mouse.move(handle!.x - 12, handle!.y + 12, { steps: 4 });
    await page.mouse.move(target!.x + target!.width / 2, target!.y + 120, { steps: 20 });
    await page.mouse.up();
    await expect(card.getByLabel('Status of Review release acceptance')).toHaveValue('TODO');
    await card.getByLabel('Status of Review release acceptance').selectOption('COMPLETED');
    await expect(
      page
        .locator('.kanban-column')
        .filter({ has: page.getByRole('heading', { name: 'Completed 1', exact: true }) })
        .getByRole('button', { name: 'Review release acceptance', exact: true }),
    ).toBeVisible();
    await page.reload();
    await expect(
      page
        .locator('.kanban-card')
        .filter({ hasText: 'Review release acceptance' })
        .getByLabel('Status of Review release acceptance'),
    ).toHaveValue('COMPLETED');
    const records = await request.get(`/api/v1/tasks?scope=all&projectId=${projectId}`, {
      headers,
    });
    const data = await records.json();
    expect(data.content).toHaveLength(1);
    expect(data.content[0].status).toBe('COMPLETED');
  } finally {
    if (projectId) {
      const current = await request.get(`/api/v1/projects/${projectId}`, { headers });
      if (current.ok()) {
        const p = await current.json();
        const archived = await request.put(`/api/v1/projects/${projectId}`, {
          headers,
          data: { ...p, status: 'ARCHIVED' },
        });
        expect(archived.ok()).toBeTruthy();
        expect(
          (await request.delete(`/api/v1/projects/${projectId}`, { headers })).ok(),
        ).toBeTruthy();
      }
    }
  }
});
test('viewer permissions and cross-tab sessions remain consistent', async ({ page, context }) => {
  await login(page, 'iris@taskflow.local');
  await page.getByRole('link', { name: 'All projects', exact: true }).click();
  await page.locator('.project-card').first().click();
  await page.getByRole('link', { name: 'List', exact: true }).click();
  await expect(page.getByRole('button', { name: 'New task', exact: true })).toHaveCount(0);
  await page.locator('.task-title').first().click();
  await expect(page.getByLabel('Task status', { exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Attach file', exact: true })).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const tabs = await Promise.all([context.newPage(), context.newPage()]);
  await Promise.all(tabs.map((tab) => tab.goto('/my-tasks')));
  for (const tab of tabs) {
    await expect(tab.getByRole('heading', { name: 'My Tasks', exact: true })).toBeVisible();
    await tab.close();
  }
  await page.reload();
  await expect(page.getByRole('heading')).not.toHaveCount(0);
});
test('all workspace screens render without browser or API errors', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await login(page);
  const badResponses: string[] = [];
  page.on('response', (r) => {
    if (r.url().includes('/api/v1') && r.status() >= 400)
      badResponses.push(`${r.status()} ${r.url()}`);
  });
  for (const [url, title] of [
    ['/overview', 'Overview'],
    ['/notifications', 'Notifications'],
    ['/calendar', 'My Calendar'],
    ['/completed', 'Completed Tasks'],
    ['/projects', 'Projects'],
    ['/team', 'Team'],
    ['/reports', 'Reports'],
    ['/search', 'Search your workspace'],
    ['/settings', 'Settings'],
  ]) {
    await page.goto(url);
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    await expect(page.locator('[aria-label="Loading"]')).toHaveCount(0);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
  }
  await page.getByRole('radio', { name: 'Dark', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.goto('/my-tasks');
  await expect(page.locator('.task-table tbody tr').first()).toBeVisible();
  for (const width of [768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(page.getByRole('button', { name: 'Open navigation' })).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
    await page.getByRole('button', { name: 'Open navigation' }).click();
    await expect(page.getByRole('link', { name: 'Settings', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Close navigation', exact: true }).last().click();
  }
  expect(errors).toEqual([]);
  expect(badResponses).toEqual([]);
});
test('admin screens enforce the authenticated role', async ({ page }) => {
  await login(page, 'admin@taskflow.local');
  await page.getByRole('link', { name: 'User management', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'User management', exact: true })).toBeVisible();
  await expect(page.locator('.admin-user-row').first()).toBeVisible();
  await page.getByRole('link', { name: 'Audit logs', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'System audit logs', exact: true })).toBeVisible();
  await expect(page.locator('.audit-table tbody tr').first()).toBeVisible();
});
