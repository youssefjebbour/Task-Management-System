import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowRight, Check, ArrowLeft, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../lib/auth';
import { post } from '../lib/api';
import { Button, FormError } from '../components/ui';

export function AuthPage({ mode }: { mode: 'login' | 'register' | 'forgot' | 'reset' }) {
  const { user, loading, login, register } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [message, setMessage] = useState(''),
    [show, setShow] = useState(false);
  if (user && (mode === 'login' || mode === 'register')) return <Navigate to="/my-tasks" replace />;
  const titles = {
    login: 'Welcome back',
    register: 'Make room for better work',
    forgot: 'Reset your password',
    reset: 'Choose a new password',
  };
  const descriptions = {
    login: 'Sign in to pick up where you left off.',
    register: 'Create your account. Bring your team together.',
    forgot: 'We’ll email you a secure link to get back in.',
    reset: 'Use at least 12 characters for a strong password.',
  };
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    const fields = Object.fromEntries(new FormData(event.currentTarget)) as Record<string, string>;
    try {
      if (mode === 'login') {
        await login(fields.email, fields.password);
        navigate('/my-tasks');
      } else if (mode === 'register') {
        await register(fields);
        navigate('/my-tasks');
      } else if (mode === 'forgot') {
        const r = await post<{ message: string }>('/auth/forgot-password', { email: fields.email });
        setMessage(r.message);
      } else {
        const r = await post<{ message: string }>('/auth/reset-password', {
          token: params.get('token'),
          password: fields.password,
        });
        setMessage(r.message);
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-page">
      <div className="auth-story">
        <Link className="brand" to="/login">
          <img src="/favicon.svg" alt="" />
          TaskFlow<span className="brand-period">.</span>
        </Link>
        <div className="auth-story-content">
          <span className="story-line">A little clarity goes a long way.</span>
          <h2>
            Good work.
            <br />
            Clear focus.
          </h2>
          <p>
            A shared space for your projects, your people, and the details that make a difference.
          </p>
          <div className="auth-principles">
            <span>
              <Check size={16} /> Clear ownership
            </span>
            <span>
              <Check size={16} /> Less scattered work
            </span>
            <span>
              <Check size={16} /> More shared progress
            </span>
          </div>
        </div>
        <span className="auth-footer">Your work, thoughtfully organized.</span>
      </div>
      <main className="auth-main">
        <div className="auth-form-wrap">
          <Link className="mobile-brand brand" to="/login">
            <img src="/favicon.svg" alt="" />
            TaskFlow
          </Link>
          <h1>{titles[mode]}</h1>
          <p>{descriptions[mode]}</p>
          {message ? (
            <div className="success-message">
              <Check size={20} />
              <p>{message}</p>
              <Link className="button button-primary" to="/login">
                Back to sign in <ArrowRight size={16} />
              </Link>
            </div>
          ) : (
            <form onSubmit={submit} className="form-stack">
              {mode === 'register' && (
                <>
                  <label>
                    Full name
                    <input
                      name="fullName"
                      autoComplete="name"
                      required
                      maxLength={100}
                      placeholder="Your full name"
                    />
                  </label>
                  <label>
                    Username
                    <input
                      name="username"
                      autoComplete="username"
                      required
                      minLength={3}
                      maxLength={40}
                      pattern="[a-zA-Z0-9_]+"
                      placeholder="Choose a username"
                    />
                  </label>
                </>
              )}
              {mode !== 'reset' && (
                <label>
                  Email address
                  <input
                    name="email"
                    type="email"
                    autoComplete="email"
                    required
                    maxLength={254}
                    placeholder="you@company.com"
                  />
                </label>
              )}
              {mode !== 'forgot' && (
                <label>
                  <span className="label-row">
                    Password
                    {mode === 'login' && <Link to="/forgot-password">Forgot password?</Link>}
                  </span>
                  <span className="password-input">
                    <input
                      name="password"
                      aria-label="Password"
                      type={show ? 'text' : 'password'}
                      autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                      required
                      minLength={mode === 'login' ? 1 : 12}
                      maxLength={72}
                      placeholder={
                        mode === 'login' ? 'Enter your password' : 'At least 12 characters'
                      }
                    />
                    <button
                      type="button"
                      aria-label={show ? 'Hide password' : 'Show password'}
                      onClick={() => setShow(!show)}
                    >
                      {show ? <EyeOff size={17} /> : <Eye size={17} />}
                    </button>
                  </span>
                </label>
              )}
              <FormError error={error} />
              <Button variant="primary" type="submit" busy={busy || loading}>
                {mode === 'login'
                  ? 'Sign in'
                  : mode === 'register'
                    ? 'Create account'
                    : mode === 'forgot'
                      ? 'Send reset link'
                      : 'Update password'}
                <ArrowRight size={16} />
              </Button>
            </form>
          )}
          {mode === 'login' || mode === 'register' ? (
            <p className="auth-switch">
              {mode === 'login' ? 'New to TaskFlow?' : 'Already have an account?'}{' '}
              <Link to={mode === 'login' ? '/register' : '/login'}>
                {mode === 'login' ? 'Create an account' : 'Sign in'}
              </Link>
            </p>
          ) : (
            <Link className="back-link" to="/login">
              <ArrowLeft size={15} /> Back to sign in
            </Link>
          )}
          <p className="auth-note">A focused space for work that matters.</p>
        </div>
      </main>
    </div>
  );
}
