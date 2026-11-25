import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useAuth } from '../hooks/useAuth';
import { useUnread } from '../hooks/useUnread';
import type { DemoAction, PageState, Watch } from '../types/api';
import { AvailabilityBadge, OutcomeBadge, StatusBadge } from '../components/Badges';
import { AlertItem } from '../components/AlertItem';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorState, Loading, Spinner } from '../components/States';
import { PriceChart } from '../components/PriceChart';
import { Icon } from '../components/Icon';
import { useToast } from '../components/Toast';
import { WatchConditionFields } from '../forms/WatchConditionFields';
import { formFromWatch, toInput, validateForm, type WatchFormState } from '../forms/watchForm';
import { availabilityLabel, dateTime, displayUrl, interval, isSafeExternalUrl, money, relativeTime, WATCH_TYPES } from '../utils/format';

function StateBlock({ label, s }: { label: string; s: PageState | null }) {
  return (
    <div className="card card-tight stack-sm">
      <span className="tiny muted">{label}</span>
      {s ? (
        <>
          <span className="price-big">{s.price != null ? money(s.price, s.currency) : <span className="muted small">No price</span>}</span>
          <div className="row"><AvailabilityBadge value={s.availability} /><span className="tiny muted">{relativeTime(s.capturedAt)}</span></div>
        </>
      ) : <span className="muted small">{label === 'Current' ? 'Not checked yet' : 'No earlier state'}</span>}
    </div>
  );
}

const DEMO_ACTIONS: { action: DemoAction; label: string; product?: boolean; keyword?: boolean }[] = [
  { action: 'PRICE_DROP', label: 'Drop price 15%', product: true },
  { action: 'PRICE_INCREASE', label: 'Raise price 10%', product: true },
  { action: 'RESTOCK', label: 'Restock', product: true },
  { action: 'SELL_OUT', label: 'Sell out', product: true },
  { action: 'ADD_KEYWORD', label: 'Add keyword', keyword: true },
  { action: 'REMOVE_KEYWORD', label: 'Remove keyword', keyword: true },
  { action: 'PAGE_CHANGE', label: 'Change page text' },
  { action: 'FAIL_NEXT', label: 'Fail next check' },
  { action: 'RESET', label: 'Reset' },
];

function DemoPanel({ watch, onDone }: { watch: Watch; onDone: () => void }) {
  const toast = useToast();
  const [busy, setBusy] = useState<string>();
  const slug = watch.source.url.replace('demo://', '');
  const [keyword, setKeyword] = useState(watch.targetKeyword ?? 'Senior Java Engineer');
  const run = async (a: DemoAction) => {
    setBusy(a);
    try {
      const r = await api.simulate(slug, a, keyword);
      toast(r.checked ? `${r.message}${r.alertsCreated ? '' : ' No alert.'}` : r.message, r.alertsCreated ? 'ok' : 'info');
      onDone();
    } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Simulation failed', 'bad');
    } finally {
      setBusy(undefined);
    }
  };
  const isProduct = watch.current?.price != null;
  return (
    <section className="card stack" aria-labelledby="demo-h" style={{ borderStyle: 'dashed' }}>
      <div className="row-between"><h2 id="demo-h" className="row" style={{ gap: 8 }}><Icon name="flask" /> Demo controls</h2><span className="badge tone-warn no-dot">Development only</span></div>
      <p className="muted small">Change this deterministic demo page, then a check runs immediately so you can see alerts end to end.</p>
      <div className="field">
        <label className="label" htmlFor="demo-kw">Keyword for add/remove</label>
        <input id="demo-kw" className="input" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
      </div>
      <div className="row" style={{ gap: 8 }}>
        {DEMO_ACTIONS.filter((d) => !d.product || isProduct).map((d) => (
          <button key={d.action} className="btn btn-sm" disabled={!!busy || (d.keyword && !keyword.trim())} onClick={() => run(d.action)}>
            {busy === d.action ? <Spinner /> : null}{d.label}
          </button>
        ))}
      </div>
    </section>
  );
}

export function WatchDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const toast = useToast();
  const { config } = useAuth();
  const { refresh: refreshUnread } = useUnread();
  const watch = useAsync(() => api.watch(id), [id]);
  const snapshots = useAsync(() => api.snapshots(id, 0, 100), [id]);
  const checks = useAsync(() => api.watchChecks(id, 0, 10), [id]);
  const alerts = useAsync(() => api.alerts({ watchId: id, size: 20 }), [id]);
  const [editing, setEditing] = useState<WatchFormState | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState(false);

  const reloadAll = () => {
    void watch.reload();
    void snapshots.reload();
    void checks.reload();
    void alerts.reload();
    refreshUnread();
  };

  const pricePoints = useMemo(() => (snapshots.data?.items ?? []).filter((s) => s.price != null).slice().reverse()
    .map((s) => ({ t: s.createdAt, v: s.price as number })), [snapshots.data]);
  const availabilityHistory = useMemo(() => {
    const items = (snapshots.data?.items ?? []).slice().reverse();
    return items.filter((s, i) => s.availability !== 'UNKNOWN' && (i === 0 || items[i - 1].availability !== s.availability)).reverse();
  }, [snapshots.data]);

  if (watch.error) {
    return watch.error instanceof ApiError && watch.error.status === 404
      ? <div className="card"><h1>Watch not found</h1><p className="muted" style={{ margin: '8px 0 16px' }}>It may have been deleted.</p><Link to="/dashboard" className="btn">Back to dashboard</Link></div>
      : <ErrorState error={watch.error} onRetry={watch.reload} />;
  }
  if (!watch.data) return <Loading />;
  const w = watch.data;

  const act = async (name: string, fn: () => Promise<Watch | void>, success: string) => {
    setBusy(name);
    try {
      const r = await fn();
      if (r) watch.setData(r);
      toast(success, 'ok');
    } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Action failed', 'bad');
    } finally {
      setBusy(undefined);
    }
  };

  const checkNow = async () => {
    setBusy('check');
    try {
      const r = await api.checkNow(id);
      watch.setData(r.watch);
      toast(r.message, r.outcome === 'FAILED' ? 'bad' : 'ok');
      reloadAll();
    } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Check failed', 'bad');
    } finally {
      setBusy(undefined);
    }
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    const v = validateForm(editing, true);
    setErrors(v);
    if (Object.keys(v).length) return;
    setBusy('save');
    try {
      const updated = await api.updateWatch(id, { ...toInput(editing), title: editing.title.trim() });
      watch.setData(updated);
      setEditing(null);
      toast('Watch updated', 'ok');
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors({ ...err.fieldErrors, _form: err.message });
      }
    } finally {
      setBusy(undefined);
    }
  };

  const toggleRead = async (a: { id: number; read: boolean }) => {
    try {
      a.read ? await api.markUnread(a.id) : await api.markRead(a.id);
      void alerts.reload();
      refreshUnread();
    } catch {
      toast('Could not update the alert', 'bad');
    }
  };

  const isDemo = w.source.sourceType === 'DEMO';
  return (
    <div className="stack" style={{ gap: 20 }}>
      <div>
        <Link to="/dashboard" className="back"><Icon name="arrowLeft" /> Dashboard</Link>
        <div className="page-head" style={{ marginBottom: 0 }}>
          <div className="grow">
            <h1 className="break">{w.title}</h1>
            <p className="muted small break" style={{ marginTop: 6 }}>
              {isSafeExternalUrl(w.source.url)
                ? <a href={w.source.url} target="_blank" rel="noopener noreferrer nofollow">{displayUrl(w.source.url)} ↗</a>
                : <span className="mono">{w.source.url}</span>}
            </p>
          </div>
          <div className="row">
            <button className="btn" onClick={checkNow} disabled={!!busy || !w.active}><Icon name="refresh" />{busy === 'check' ? 'Checking…' : 'Check now'}</button>
            {w.active
              ? <button className="btn" onClick={() => act('pause', () => api.pauseWatch(id), 'Watch paused')} disabled={!!busy}><Icon name="pause" />Pause</button>
              : <button className="btn btn-primary" onClick={() => act('resume', () => api.resumeWatch(id), 'Watch resumed')} disabled={!!busy}><Icon name="play" />Resume</button>}
            <button className="btn" onClick={() => { setEditing(formFromWatch(w)); setErrors({}); }} disabled={!!editing}><Icon name="edit" />Edit</button>
            <button className="btn btn-danger" onClick={() => setConfirmDelete(true)} aria-label="Delete watch"><Icon name="trash" /></button>
          </div>
        </div>
      </div>

      {!w.active && (
        <div className="notice notice-info small">
          {w.completedAt ? (w.expiresAt && new Date(w.expiresAt) <= new Date(w.completedAt) ? 'This watch expired.' : 'One-time alert sent — this watch is complete. Resume it to keep watching.') : 'This watch is paused. No checks or alerts until you resume it.'}
        </div>
      )}
      {w.active && w.source.status !== 'ACTIVE' && w.source.status !== 'PENDING' && (
        <div className={'notice small ' + (w.source.status === 'DEGRADED' ? 'notice-warn' : 'notice-bad')} role="status">
          <Icon name="alert" />
          <div><strong>Monitoring issue.</strong> {w.source.statusMessage ?? 'Recent checks failed.'} Next attempt {relativeTime(w.source.nextFetchAt)}.</div>
        </div>
      )}

      {editing && (
        <form className="card stack fade-in" onSubmit={save} noValidate aria-label="Edit watch">
          <h2>Edit watch</h2>
          <div className="field">
            <label className="label" htmlFor="edit-title">Name</label>
            <input id="edit-title" className="input" value={editing.title} maxLength={200} aria-invalid={!!errors.title}
              onChange={(e) => setEditing({ ...editing, title: e.target.value })} />
            {errors.title && <span className="field-error">{errors.title}</span>}
          </div>
          <WatchConditionFields value={editing} onChange={setEditing} errors={errors} currency={w.current?.currency}
            intervals={config?.intervalOptions ?? [15, 30, 60, 180, 360, 720, 1440]} />
          <p className="hint">Changing the condition sets a fresh baseline, so the change itself never triggers an alert.</p>
          {errors._form && <div className="notice notice-bad small" role="alert">{errors._form}</div>}
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>Cancel</button>
            <button className="btn btn-primary" disabled={busy === 'save'}>{busy === 'save' ? 'Saving…' : 'Save changes'}</button>
          </div>
        </form>
      )}

      <div className="grid-2" style={{ alignItems: 'start' }}>
        <section className="card stack" aria-labelledby="cond-h">
          <div className="row-between"><h2 id="cond-h">Condition</h2>{w.conditionMet && w.active && <span className="badge tone-ok">Met</span>}</div>
          <div>
            <div style={{ fontWeight: 600 }}>{WATCH_TYPES[w.watchType].label}
              {w.watchType === 'PRICE_BELOW' && w.targetPrice != null && <> · {money(w.targetPrice, w.current?.currency)}</>}
              {w.targetKeyword && <> · “{w.targetKeyword}”</>}
            </div>
            <div className="text-2 small" style={{ marginTop: 4 }}>{w.conditionState}</div>
          </div>
          <dl className="kv">
            <dt>Alert mode</dt><dd>{w.alertMode === 'ONE_TIME' ? 'Once, then stop' : 'Every time'}</dd>
            <dt>Cooldown</dt><dd>{w.cooldownMinutes === 0 ? 'None' : `${w.cooldownMinutes} min`}</dd>
            <dt>Last alert</dt><dd>{w.lastTriggeredAt ? relativeTime(w.lastTriggeredAt) : 'None yet'}</dd>
            {w.expiresAt && <><dt>Expires</dt><dd>{dateTime(w.expiresAt)}</dd></>}
          </dl>
        </section>
        <section className="card stack" aria-labelledby="health-h">
          <div className="row-between"><h2 id="health-h">Monitoring</h2><StatusBadge status={w.source.status} /></div>
          <dl className="kv">
            <dt>Frequency</dt><dd>{interval(w.source.checkIntervalMinutes)}{w.source.checkIntervalMinutes !== w.checkIntervalMinutes && <span className="tiny muted"> (shared)</span>}</dd>
            <dt>Last checked</dt><dd title={dateTime(w.source.lastFetchedAt)}>{relativeTime(w.source.lastFetchedAt)}</dd>
            <dt>Last success</dt><dd title={dateTime(w.source.lastSuccessAt)}>{relativeTime(w.source.lastSuccessAt)}</dd>
            <dt>Next check</dt><dd title={dateTime(w.source.nextFetchAt)}>{w.active ? relativeTime(w.source.nextFetchAt) : 'Paused'}</dd>
            {w.source.failureCount > 0 && <><dt>Consecutive failures</dt><dd>{w.source.failureCount}</dd></>}
          </dl>
        </section>
      </div>

      <section className="stack" aria-label="Current and previous state">
        <div className="grid-2">
          <StateBlock label="Current" s={w.current} />
          <StateBlock label="Previous" s={w.previous} />
        </div>
      </section>

      {isDemo && config?.demoEnabled && <DemoPanel watch={w} onDone={reloadAll} />}

      <div className="grid-2" style={{ alignItems: 'start' }}>
        <section className="card stack" aria-labelledby="price-h">
          <h2 id="price-h">Price history</h2>
          {snapshots.data ? <PriceChart points={pricePoints} currency={w.current?.currency ?? null} /> : <div className="skeleton" style={{ height: 160 }} />}
        </section>
        <section className="card stack" aria-labelledby="avail-h">
          <h2 id="avail-h">Availability history</h2>
          {availabilityHistory.length === 0 ? <p className="muted small">No availability information for this page.</p> : (
            <div className="timeline">
              {availabilityHistory.slice(0, 8).map((s) => (
                <div key={s.id} className="timeline-item"><span className="tiny muted">{relativeTime(s.createdAt)}</span><span>{availabilityLabel(s.availability)}</span></div>
              ))}
            </div>
          )}
        </section>
      </div>

      <section className="card" aria-labelledby="alerts-h">
        <div className="card-head"><h2 id="alerts-h">Alert history</h2></div>
        {alerts.error ? <ErrorState error={alerts.error} onRetry={alerts.reload} /> : !alerts.data ? <div className="skeleton" style={{ height: 60 }} />
          : alerts.data.items.length === 0 ? <p className="muted small">No alerts for this watch yet.</p>
            : <div className="list">{alerts.data.items.map((a) => <AlertItem key={a.id} alert={a} onToggleRead={toggleRead} />)}</div>}
      </section>

      <section className="card" aria-labelledby="checks-h">
        <div className="card-head"><h2 id="checks-h">Recent checks</h2><Link to="/history" className="small">All history</Link></div>
        {!checks.data ? <div className="skeleton" style={{ height: 60 }} /> : checks.data.items.length === 0 ? <p className="muted small">The first check will run shortly.</p> : (
          <div className="timeline">
            {checks.data.items.map((c) => (
              <div key={c.id} className="timeline-item">
                <span className="tiny muted" title={dateTime(c.startedAt)}>{relativeTime(c.startedAt)}</span>
                <div className="row" style={{ gap: 8 }}><OutcomeBadge outcome={c.outcome} /><span className="small text-2 break">{c.message}</span>
                  {c.httpStatus && c.outcome !== 'UNCHANGED' && <span className="tiny muted">HTTP {c.httpStatus}</span>}</div>
              </div>
            ))}
          </div>
        )}
      </section>

      {confirmDelete && (
        <ConfirmDialog title="Delete this watch?" confirmLabel="Delete" danger busy={busy === 'delete'}
          onCancel={() => setConfirmDelete(false)}
          onConfirm={async () => {
            setBusy('delete');
            try {
              await api.deleteWatch(id);
              toast('Watch deleted', 'ok');
              refreshUnread();
              navigate('/dashboard');
            } catch (e) {
              toast(e instanceof ApiError ? e.message : 'Delete failed', 'bad');
              setBusy(undefined);
              setConfirmDelete(false);
            }
          }}>
          Its alerts and history will be removed. This can’t be undone.
        </ConfirmDialog>
      )}
    </div>
  );
}
