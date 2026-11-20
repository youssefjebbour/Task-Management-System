import { test, expect } from '@playwright/test';
import fs from 'node:fs';

test('registration, empty workspace and email recovery work against the real services', async ({
  page,
  request,
}) => {
  const suffix = Date.now().toString(36);
  const email = `browser-recovery-${suffix}@example.test`;
  const password = `Browser-test-${suffix}!`;
  let id = '';
  try {
    await page.goto('/register');
    await page.getByLabel('Full name').fill('Browser recovery verification');
    await page.getByLabel('Username').fill(`browser_${suffix}`);
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(password);
    const registered = page.waitForResponse((r) => r.url().endsWith('/auth/register'));
    await page.getByRole('button', { name: 'Create account', exact: true }).click();
    const session = await (await registered).json();
    id = session.user.id;
    await expect(page.getByRole('heading', { name: 'My Tasks', exact: true })).toBeVisible();
    await expect(page.locator('.empty-state')).toBeVisible();
    await page.context().clearCookies();
    await page.goto('/forgot-password');
    await page.getByLabel('Email address').fill(email);
    await page.getByRole('button', { name: 'Send reset link', exact: true }).click();
    await expect(page.locator('.success-message')).toBeVisible();
    let messageId = '';
    await expect
      .poll(
        async () => {
          const messages = await (
            await request.get('http://localhost:8025/api/v1/messages')
          ).json();
          messageId =
            messages.messages.find((m: { To: { Address: string }[] }) =>
              m.To.some((to) => to.Address === email),
            )?.ID || '';
          return !!messageId;
        },
        { timeout: 15000 },
      )
      .toBeTruthy();
    const message = await (
      await request.get(`http://localhost:8025/api/v1/message/${messageId}`)
    ).json();
    const link = message.Text.match(/https?:\/\/[^\s]+\/reset-password\?token=[^\s]+/)[0];
    await page.goto(new URL(link).pathname + new URL(link).search);
    await page.getByLabel('Password', { exact: true }).fill(`${password}-new`);
    await page.getByRole('button', { name: 'Update password', exact: true }).click();
    await expect(page.locator('.success-message')).toBeVisible();
    await page.goto('/login');
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(`${password}-new`);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'My Tasks', exact: true })).toBeVisible();
    await request.delete(`http://localhost:8025/api/v1/messages`, { data: { IDs: [messageId] } });
  } finally {
    if (id) {
      const env = fs.existsSync('../.env') ? fs.readFileSync('../.env', 'utf8') : '';
      const seedPassword =
        process.env.SEED_PASSWORD ||
        env
          .split(/\r?\n/)
          .find((l) => l.startsWith('SEED_PASSWORD='))
          ?.slice(14);
      const admin = await request.post('/api/v1/auth/login', {
        data: { email: 'admin@taskflow.local', password: seedPassword },
      });
      const token = (await admin.json()).accessToken;
      expect(
        (
          await request.patch(`/api/v1/users/${id}`, {
            headers: { Authorization: `Bearer ${token}` },
            data: { active: false, systemRole: 'USER' },
          })
        ).ok(),
      ).toBeTruthy();
    }
  }
});
