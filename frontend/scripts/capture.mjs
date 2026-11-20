import { chromium, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '../..');
const env = Object.fromEntries(
  fs
    .readFileSync(path.join(root, '.env'), 'utf8')
    .split(/\r?\n/)
    .filter((x) => x.includes('='))
    .map((x) => [x.slice(0, x.indexOf('=')), x.slice(x.indexOf('=') + 1)]),
);
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
const base = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173';
const directory = path.join(root, 'docs/screenshots');
fs.mkdirSync(directory, { recursive: true });
async function capture(name, url) {
  if (url) await page.goto(base + url);
  await page.waitForLoadState('networkidle');
  await expect(page.locator('[aria-label="Loading"]')).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: path.join(directory, name + '.png'),
    fullPage: false,
    animations: 'disabled',
  });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth))
    errors.push('Page overflow: ' + name);
}
try {
  for (const [name, url] of [
    ['login', '/login'],
    ['registration', '/register'],
    ['recovery', '/forgot-password'],
  ])
    await capture(name, url);
  await page.goto(base + '/login');
  await page.getByLabel('Email address').fill('maya@taskflow.local');
  await page.getByLabel('Password', { exact: true }).fill(env.SEED_PASSWORD);
  const auth = page.waitForResponse(
    (r) => r.url().endsWith('/auth/login') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const session = await (await auth).json();
  await page.getByRole('heading', { name: 'My Tasks', exact: true }).waitFor();
  await capture('my-tasks-desktop');
  await page.locator('.task-title').first().click();
  await expect(page.getByRole('heading', { name: 'Attachments', exact: true })).toBeAttached();
  await capture('task-drawer');
  await page.keyboard.press('Escape');
  for (const [name, url] of [
    ['overview', '/overview'],
    ['notifications', '/notifications'],
    ['calendar', '/calendar'],
    ['completed', '/completed'],
    ['projects', '/projects'],
    ['team', '/team'],
    ['reports', '/reports'],
    ['search', '/search'],
    ['settings', '/settings'],
  ])
    await capture(name, url);
  const projects = await (
    await page.request.get(base + '/api/v1/projects', {
      headers: { Authorization: 'Bearer ' + session.accessToken },
    })
  ).json();
  const project = projects.content.find((p) => p.projectKey === 'WEB') || projects.content[0];
  for (const tab of ['overview', 'board', 'list', 'members', 'settings'])
    await capture(tab === 'board' ? 'board' : 'project-' + tab, `/projects/${project.id}/${tab}`);
  await page.goto(base + '/settings');
  await page.getByRole('radio', { name: 'Dark', exact: true }).click();
  await capture('dark-theme', '/my-tasks');
  await page.goto(base + '/settings');
  await page.getByRole('radio', { name: 'Light', exact: true }).click();
  for (const width of [768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await capture('my-tasks-' + width, '/my-tasks');
  }
  await page.setViewportSize({ width: 390, height: 900 });
  await capture('calendar-mobile', '/calendar');
  await capture('board-mobile', `/projects/${project.id}/board`);
  await context.clearCookies();
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto(base + '/login');
  await page.getByLabel('Email address').fill('admin@taskflow.local');
  await page.getByLabel('Password', { exact: true }).fill(env.SEED_PASSWORD);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.getByRole('heading', { name: 'My Tasks', exact: true }).waitFor();
  await capture('admin-users', '/admin/users');
  await capture('admin-audit', '/admin/audit');
  if (errors.length) throw new Error(JSON.stringify(errors));
  console.log(
    'Captured workspace, project, auth and admin screens; no browser errors or horizontal overflow.',
  );
} finally {
  await browser.close();
}
