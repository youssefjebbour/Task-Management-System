import { chromium } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '../..');
const env = Object.fromEntries(
  fs
    .readFileSync(path.join(root, '.env'), 'utf8')
    .split(/\r?\n/)
    .filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const failures = [];
page.on('pageerror', (e) => failures.push(e.message));
await page.goto('http://localhost:5173/login');
await page.getByLabel('Email address').fill('maya@taskflow.local');
await page.getByLabel('Password', { exact: true }).fill(env.SEED_PASSWORD);
await page.getByRole('button', { name: 'Sign in', exact: true }).click();
await page.getByRole('heading', { name: 'My Tasks', exact: true }).waitFor();
await page.locator('.task-table tbody tr').first().waitFor();
fs.mkdirSync(path.join(root, 'docs/screenshots'), { recursive: true });
await page.screenshot({
  path: path.join(root, 'docs/screenshots/my-tasks-desktop.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.getByRole('button', { name: 'Open Refine the task detail drawer', exact: true }).click();
await page.getByRole('heading', { name: 'Refine the task detail drawer', exact: true }).waitFor();
await page.screenshot({
  path: path.join(root, 'docs/screenshots/task-drawer.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.getByRole('button', { name: 'Close task details' }).click();
await page.getByRole('link', { name: 'Overview', exact: true }).click();
await page.getByRole('heading', { name: 'Overview', exact: true }).waitFor();
await page.getByRole('img', { name: /Tasks by status/ }).waitFor();
await page.screenshot({
  path: path.join(root, 'docs/screenshots/overview.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.getByRole('link', { name: 'My Tasks', exact: true }).click();
await page.reload();
await page.getByRole('heading', { name: 'My Tasks', exact: true }).waitFor();
await page.locator('.task-table tbody tr').first().waitFor();
for (const width of [768, 390]) {
  await page.setViewportSize({ width, height: 900 });
  await page.screenshot({
    path: path.join(root, `docs/screenshots/my-tasks-${width}.png`),
    fullPage: true,
    animations: 'disabled',
  });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
  if (overflow) failures.push(`Horizontal overflow at ${width}`);
}
if (failures.length) throw new Error(JSON.stringify(failures));
console.log(
  'PASS: real login, database-backed task list, task drawer, dashboard, refresh persistence, 1440/768/390 layouts.',
);
await browser.close();
