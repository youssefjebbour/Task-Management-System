import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Moon, Sun, Monitor, Check } from 'lucide-react';
import { put, post } from '../lib/api';
import { useAuth } from '../lib/auth';
import { Avatar, Button, FormError, PageHeading, useToast } from '../components/ui';
export function applyTheme(theme: string) {
  localStorage.setItem('tf-theme', theme);
  document.documentElement.dataset.theme =
    theme === 'system'
      ? matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark'
        : 'light'
      : theme;
}
export function SettingsPage() {
  const { user, reload, logout } = useAuth();
  const toast = useToast();
  const [theme, setTheme] = useState(localStorage.getItem('tf-theme') || 'light');
  const profile = useMutation({
    mutationFn: (body: unknown) => put('/users/me', body),
    onSuccess: async () => {
      await reload();
      toast('Profile saved');
    },
  });
  const password = useMutation({
    mutationFn: (body: unknown) => post('/users/me/password', body),
    onSuccess: async () => {
      toast('Password changed. Sign in again.');
      await logout();
    },
  });
  function save(e: FormEvent<HTMLFormElement>, kind: string) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));
    (kind === 'profile' ? profile : password).mutate(data);
  }
  if (!user) return null;
  return (
    <div className="page settings-page">
      <PageHeading title="Settings" description="A workspace that feels like yours." />
      <section className="settings-section">
        <div>
          <h2>Your profile</h2>
          <p>Your teammates will see this name and role.</p>
        </div>
        <form className="settings-panel form-stack" onSubmit={(e) => save(e, 'profile')}>
          <div className="profile-preview">
            <Avatar person={user} size="large" />
            <div>
              <strong>{user.fullName}</strong>
              <span>{user.email}</span>
            </div>
          </div>
          <label>
            Full name
            <input name="fullName" defaultValue={user.fullName} required maxLength={100} />
          </label>
          <label>
            Job title
            <input
              name="jobTitle"
              defaultValue={user.jobTitle ?? ''}
              maxLength={120}
              placeholder="What do you do?"
            />
          </label>
          <label>
            Username
            <input disabled value={user.username} />
          </label>
          <FormError error={profile.error} />
          <div className="form-actions">
            <Button variant="primary" busy={profile.isPending}>
              Save profile
            </Button>
          </div>
        </form>
      </section>
      <section className="settings-section">
        <div>
          <h2>Appearance</h2>
          <p>Choose the backdrop for your best work.</p>
        </div>
        <div className="theme-options" role="radiogroup" aria-label="Color theme">
          {[
            { value: 'light', label: 'Light', icon: Sun },
            { value: 'dark', label: 'Dark', icon: Moon },
            { value: 'system', label: 'System', icon: Monitor },
          ].map((t) => (
            <button
              key={t.value}
              role="radio"
              aria-checked={theme === t.value}
              tabIndex={theme === t.value ? 0 : -1}
              onKeyDown={(event) => {
                if (
                  !['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(
                    event.key,
                  )
                )
                  return;
                event.preventDefault();
                const values = ['light', 'dark', 'system'];
                const index = values.indexOf(t.value);
                const next =
                  event.key === 'Home'
                    ? 0
                    : event.key === 'End'
                      ? 2
                      : (index + (['ArrowLeft', 'ArrowUp'].includes(event.key) ? 2 : 1)) % 3;
                setTheme(values[next]);
                applyTheme(values[next]);
                (
                  event.currentTarget.parentElement?.querySelectorAll('[role="radio"]')[
                    next
                  ] as HTMLButtonElement
                )?.focus();
              }}
              className={theme === t.value ? 'selected' : ''}
              onClick={() => {
                setTheme(t.value);
                applyTheme(t.value);
              }}
            >
              <t.icon size={21} />
              <span>{t.label}</span>
              {theme === t.value && <Check size={14} />}
            </button>
          ))}
        </div>
      </section>
      <section className="settings-section">
        <div>
          <h2>Password & security</h2>
          <p>
            Use a unique password of at least 12 characters. Changing it signs you out of all
            sessions.
          </p>
        </div>
        <form className="settings-panel form-stack" onSubmit={(e) => save(e, 'password')}>
          <label>
            Current password
            <input
              type="password"
              name="currentPassword"
              required
              autoComplete="current-password"
              maxLength={72}
            />
          </label>
          <label>
            New password
            <input
              type="password"
              name="newPassword"
              required
              minLength={12}
              maxLength={72}
              autoComplete="new-password"
            />
          </label>
          <FormError error={password.error} />
          <div className="form-actions">
            <Button busy={password.isPending}>Change password</Button>
          </div>
        </form>
      </section>
    </div>
  );
}
