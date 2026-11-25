import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useAuth } from '../hooks/useAuth';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorState, Loading, Spinner } from '../components/States';
import { useToast } from '../components/Toast';
import { currentSubscription, disablePushOnThisDevice, enablePush, pushSupport } from '../utils/push';
import { relativeTime } from '../utils/format';

function PushSection() {
  const { config } = useAuth();
  const toast = useToast();
  const settings = useAsync(() => api.settings(), []);
  const devices = useAsync(() => api.pushDevices(), []);
  const [thisDevice, setThisDevice] = useState<string | null>(null);
  const [busy, setBusy] = useState<string>();
  const support = pushSupport();
  const permission = typeof Notification !== 'undefined' ? Notification.permission : 'default';

  useEffect(() => {
    currentSubscription().then((s) => setThisDevice(s?.endpoint ?? null)).catch(() => setThisDevice(null));
  }, []);

  const run = async (name: string, fn: () => Promise<void>) => {
    setBusy(name);
    try {
      await fn();
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Something went wrong', 'bad');
    } finally {
      setBusy(undefined);
      void devices.reload();
      void settings.reload();
    }
  };

  if (!config?.pushConfigured) {
    return (
      <div className="notice notice-info small">
        Browser push isn’t configured on this server yet, so alerts appear in your notification center only.
        An administrator can enable it by setting <span className="code-chip">VAPID_PUBLIC_KEY</span> and <span className="code-chip">VAPID_PRIVATE_KEY</span>.
      </div>
    );
  }
  if (settings.error) return <ErrorState error={settings.error} onRetry={settings.reload} />;
  const s = settings.data;
  return (
    <div className="stack">
      <label className="switch">
        <input type="checkbox" checked={s?.pushEnabled ?? true} disabled={!s || busy === 'toggle'}
          onChange={(e) => run('toggle', async () => { await api.updateSettings(e.target.checked); toast(e.target.checked ? 'Push notifications on' : 'Push notifications paused', 'ok'); })} />
        <span>Send push notifications to my devices</span>
      </label>

      {support === 'insecure' && <div className="notice notice-warn small">Push requires a secure (HTTPS) connection. Open NextWindow over HTTPS or on localhost.</div>}
      {support === 'needs-install' && <div className="notice notice-warn small">On iPhone and iPad, add NextWindow to your Home Screen (Share → Add to Home Screen), open it from there, then enable push.</div>}
      {support === 'unsupported' && <div className="notice notice-warn small">This browser doesn’t support web push notifications. In-app notifications still work.</div>}
      {support === 'supported' && permission === 'denied' && <div className="notice notice-bad small">Notifications are blocked for this site. Allow them in your browser’s site settings, then reload this page.</div>}

      {support === 'supported' && (
        <div className="row">
          {thisDevice
            ? <button className="btn" disabled={!!busy} onClick={() => run('off', async () => { await disablePushOnThisDevice(); setThisDevice(null); toast('Push disabled on this device', 'ok'); })}>Disable on this device</button>
            : <button className="btn btn-primary" disabled={!!busy || permission === 'denied'} onClick={() => run('on', async () => {
              await enablePush(config.vapidPublicKey!);
              setThisDevice((await currentSubscription())?.endpoint ?? null);
              toast('Push enabled on this device', 'ok');
            })}>{busy === 'on' ? <><Spinner /> Enabling…</> : 'Enable push on this device'}</button>}
          <button className="btn" disabled={!!busy || !devices.data?.length} onClick={() => run('test', async () => {
            const r = await api.pushTest();
            toast(r.message, r.delivered > 0 ? 'ok' : 'bad');
          })}>{busy === 'test' ? 'Sending…' : 'Send test notification'}</button>
        </div>
      )}

      <div>
        <h3 style={{ marginBottom: 8 }}>Devices</h3>
        {!devices.data ? <div className="skeleton" style={{ height: 40 }} /> : devices.data.length === 0 ? <p className="muted small">No devices have push enabled.</p> : (
          <div className="list">
            {devices.data.map((d) => (
              <div key={d.id} className="list-item" style={{ alignItems: 'center' }}>
                <div className="grow">
                  <div style={{ fontWeight: 600 }}>{d.device}</div>
                  <div className="tiny muted">Added {relativeTime(d.createdAt)} · {d.lastSuccessAt ? `last delivered ${relativeTime(d.lastSuccessAt)}` : 'not delivered yet'}{d.failureCount ? ` · ${d.failureCount} recent failures` : ''}</div>
                </div>
                <button className="btn btn-ghost btn-sm" disabled={!!busy} onClick={() => run('rm' + d.id, async () => { await api.pushRemoveDevice(d.id); toast('Device removed', 'ok'); })}>Remove</button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function PasswordSection() {
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(undefined);
    if (next.length < 8 || !/[a-zA-Z]/.test(next) || !/[^a-zA-Z]/.test(next)) {
      setError('New password needs 8+ characters with letters and a number or symbol.');
      return;
    }
    setBusy(true);
    try {
      await api.changePassword(current, next);
      setCurrent('');
      setNext('');
      toast('Password changed. Other sessions were signed out.', 'ok');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change password');
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="stack" onSubmit={submit} noValidate>
      <div className="grid-2">
        <div className="field"><label className="label" htmlFor="cur-pw">Current password</label>
          <input id="cur-pw" className="input" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} /></div>
        <div className="field"><label className="label" htmlFor="new-pw">New password</label>
          <input id="new-pw" className="input" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} /></div>
      </div>
      {error && <div className="notice notice-bad small" role="alert">{error}</div>}
      <div><button className="btn" disabled={busy || !current || !next}>{busy ? 'Saving…' : 'Change password'}</button></div>
    </form>
  );
}

function DangerSection() {
  const { clear } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  return (
    <>
      <p className="muted small">Deletes your account, watches, alerts and devices permanently.</p>
      <div><button className="btn btn-danger" onClick={() => setOpen(true)}>Delete account</button></div>
      {open && (
        <ConfirmDialog title="Delete your account?" confirmLabel="Delete account" danger busy={busy} onCancel={() => setOpen(false)}
          onConfirm={async () => {
            setBusy(true);
            setError(undefined);
            try {
              await api.deleteAccount(password);
              clear();
              navigate('/');
            } catch (e) {
              setError(e instanceof ApiError ? e.message : 'Could not delete account');
              setBusy(false);
            }
          }}>
          <div className="stack-sm">
            <span>This can’t be undone. Enter your password to confirm.</span>
            <input className="input" type="password" aria-label="Password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
            {error && <span className="field-error">{error}</span>}
          </div>
        </ConfirmDialog>
      )}
    </>
  );
}

export function SettingsPage() {
  const { user } = useAuth();
  if (!user) return <Loading />;
  return (
    <div className="stack" style={{ gap: 20, maxWidth: 820, margin: '0 auto' }}>
      <div className="page-head" style={{ marginBottom: 0 }}><div><h1>Settings</h1><p className="muted">{user.email}</p></div></div>
      <section className="card stack" aria-labelledby="push-h"><h2 id="push-h">Push notifications</h2><PushSection /></section>
      <section className="card stack" aria-labelledby="pw-h"><h2 id="pw-h">Password</h2><PasswordSection /></section>
      <section className="card stack" aria-labelledby="danger-h"><h2 id="danger-h">Danger zone</h2><DangerSection /></section>
    </div>
  );
}
