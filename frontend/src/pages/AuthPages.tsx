import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../services/api';

function AuthForm({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const from = (location.state as { from?: string } | null)?.from ?? (mode === 'register' ? '/watches/new' : '/dashboard');

  const validate = () => {
    const e: Record<string, string> = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) e.email = 'Enter a valid email address';
    if (mode === 'register') {
      if (password.length < 8) e.password = 'Use at least 8 characters';
      else if (!/[a-zA-Z]/.test(password) || !/[^a-zA-Z]/.test(password)) e.password = 'Include letters and at least one number or symbol';
    } else if (!password) e.password = 'Enter your password';
    return e;
  };

  const submit = async (ev: FormEvent) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    setError(undefined);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      await (mode === 'login' ? login(email.trim(), password) : register(email.trim(), password));
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        setError(err.message);
      } else setError('Something went wrong. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-wrap">
      <form className="card auth-card stack" onSubmit={submit} noValidate aria-labelledby="auth-title">
        <div>
          <h1 id="auth-title">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
          <p className="muted small" style={{ marginTop: 6 }}>
            {mode === 'login' ? 'Sign in to see your watches and alerts.' : 'Free forever. Start watching pages in under a minute.'}
          </p>
        </div>
        {error && <div className="notice notice-bad small" role="alert">{error}</div>}
        <div className="field">
          <label className="label" htmlFor="email">Email</label>
          <input id="email" className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
            aria-invalid={!!errors.email} aria-describedby="email-err" autoFocus />
          {errors.email && <span id="email-err" className="field-error">{errors.email}</span>}
        </div>
        <div className="field">
          <label className="label" htmlFor="password">Password</label>
          <input id="password" className="input" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={password} onChange={(e) => setPassword(e.target.value)} aria-invalid={!!errors.password} aria-describedby="pw-err" />
          {errors.password && <span id="pw-err" className="field-error">{errors.password}</span>}
          {mode === 'register' && !errors.password && <span className="hint">At least 8 characters, with a number or symbol.</span>}
        </div>
        <button className="btn btn-primary btn-lg btn-block" disabled={busy}>
          {busy ? (mode === 'login' ? 'Signing in…' : 'Creating account…') : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
        <p className="small muted" style={{ textAlign: 'center' }}>
          {mode === 'login' ? <>New here? <Link to="/register">Create an account</Link></> : <>Already have an account? <Link to="/login">Sign in</Link></>}
        </p>
      </form>
    </div>
  );
}

export const LoginPage = () => <AuthForm mode="login" />;
export const RegisterPage = () => <AuthForm mode="register" />;
