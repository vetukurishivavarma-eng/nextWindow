import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { WatchCard } from '../components/WatchCard';
import { AlertItem } from '../components/AlertItem';
import { EmptyState, ErrorState, SkeletonCards } from '../components/States';
import { OutcomeBadge, StatusBadge } from '../components/Badges';
import { Icon } from '../components/Icon';
import { relativeTime } from '../utils/format';

export function DashboardPage() {
  const dash = useAsync(() => api.dashboard(), []);
  const [filter, setFilter] = useState<'all' | 'active' | 'paused'>('all');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const watches = useAsync(() => api.watches({ filter, q, page, size: 12 }), [filter, q, page]);
  const [quickUrl, setQuickUrl] = useState('');
  const navigate = useNavigate();

  const quickAdd = (e: FormEvent) => {
    e.preventDefault();
    if (quickUrl.trim()) navigate('/watches/new?url=' + encodeURIComponent(quickUrl.trim()));
  };

  const d = dash.data;
  const h = d?.health;
  return (
    <div className="stack" style={{ gap: 24 }}>
      <div className="page-head" style={{ marginBottom: 0 }}>
        <div>
          <h1>Dashboard</h1>
          <p className="muted">{h ? (h.nextCheckAt ? `Next check ${relativeTime(h.nextCheckAt)}` : 'No active monitoring yet') : ' '}</p>
        </div>
        <Link to="/watches/new" className="btn btn-primary"><Icon name="plus" /> New watch</Link>
      </div>

      <form className="card card-tight" onSubmit={quickAdd} aria-label="Quick add watch">
        <div className="input-group">
          <label htmlFor="quick" className="sr-only">Page URL</label>
          <input id="quick" className="input" placeholder="Paste a product or page URL to start watching…" value={quickUrl}
            onChange={(e) => setQuickUrl(e.target.value)} inputMode="url" />
          <button className="btn btn-primary" disabled={!quickUrl.trim()}>Analyze</button>
        </div>
      </form>

      {dash.error ? <ErrorState error={dash.error} onRetry={dash.reload} /> : (
        <div className="grid-stats">
          {[
            ['Active watches', d?.activeWatches],
            ['Unread alerts', d?.unreadAlerts],
            ['Alerts, last 7 days', d?.alertsLast7Days],
            ['Healthy sources', h ? `${h.healthy}/${h.sources}` : undefined],
          ].map(([label, value]) => (
            <div key={label as string} className="card stat">
              <div className="stat-value">{value ?? <span className="skeleton" style={{ display: 'inline-block', width: 40, height: 28 }} />}</div>
              <div className="stat-label">{label}</div>
            </div>
          ))}
        </div>
      )}

      {h && h.attention.length > 0 && (
        <section className="card" aria-labelledby="attention-h">
          <div className="card-head"><h2 id="attention-h">Needs attention</h2><span className="badge tone-warn">{h.attention.length}</span></div>
          <div className="list">
            {h.attention.map((a) => (
              <div key={a.watchId + a.status} className="list-item">
                <div className="grow stack-sm" style={{ gap: 4 }}>
                  <div className="row-between"><Link to={`/watches/${a.watchId}`} style={{ color: 'var(--text)', fontWeight: 600 }}>{a.watchTitle}</Link><StatusBadge status={a.status} /></div>
                  <p className="muted small break">{a.message}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="grid-2" style={{ alignItems: 'start' }}>
        <section className="card" aria-labelledby="recent-alerts-h">
          <div className="card-head"><h2 id="recent-alerts-h">Recent alerts</h2><Link to="/notifications" className="small">View all</Link></div>
          {!d ? <SkeletonCards count={2} /> : d.recentAlerts.length === 0
            ? <p className="muted small">No alerts yet. We'll notify you as soon as a condition is met.</p>
            : <div className="list">{d.recentAlerts.map((a) => <AlertItem key={a.id} alert={a} compact />)}</div>}
        </section>
        <section className="card" aria-labelledby="recent-changes-h">
          <div className="card-head"><h2 id="recent-changes-h">Recent changes</h2><Link to="/history" className="small">History</Link></div>
          {!d ? <SkeletonCards count={2} /> : d.recentChanges.length === 0
            ? <p className="muted small">Detected changes on your pages will appear here.</p>
            : (
              <div className="list">
                {d.recentChanges.map((c) => (
                  <div key={c.id} className="list-item">
                    <div className="grow stack-sm" style={{ gap: 2 }}>
                      <div className="row-between">
                        {c.watchId ? <Link to={`/watches/${c.watchId}`} className="truncate" style={{ color: 'var(--text)', fontWeight: 600 }}>{c.watchTitle}</Link> : <span>{c.sourceUrl}</span>}
                        <OutcomeBadge outcome={c.outcome} />
                      </div>
                      <span className="muted small">{c.message} · {relativeTime(c.startedAt)}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
        </section>
      </div>

      <section aria-labelledby="watches-h" className="stack">
        <div className="row-between">
          <h2 id="watches-h">Your watches</h2>
          <div className="row">
            <label htmlFor="search" className="sr-only">Search watches</label>
            <input id="search" className="input" style={{ height: 36, width: 180 }} placeholder="Search…" value={q}
              onChange={(e) => { setQ(e.target.value); setPage(0); }} />
            <div className="segmented" role="group" aria-label="Filter watches">
              {(['all', 'active', 'paused'] as const).map((f) => (
                <button key={f} aria-pressed={filter === f} onClick={() => { setFilter(f); setPage(0); }}>{f[0].toUpperCase() + f.slice(1)}</button>
              ))}
            </div>
          </div>
        </div>
        {watches.error ? <ErrorState error={watches.error} onRetry={watches.reload} />
          : !watches.data ? <SkeletonCards />
            : watches.data.items.length === 0 ? (
              <div className="card">
                <EmptyState icon="plus" title={q || filter !== 'all' ? 'No matching watches' : 'No watches yet'}
                  action={!q && filter === 'all' && <Link to="/watches/new" className="btn btn-primary">Create your first watch</Link>}>
                  {q || filter !== 'all' ? 'Try another search or filter.' : 'Paste a product or page link and choose what you want to be notified about.'}
                </EmptyState>
              </div>
            ) : (
              <>
                <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 320px), 1fr))' }}>
                  {watches.data.items.map((w) => <WatchCard key={w.id} watch={w} />)}
                </div>
                {watches.data.totalPages > 1 && (
                  <div className="row" style={{ justifyContent: 'center' }}>
                    <button className="btn btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
                    <span className="small muted">Page {page + 1} of {watches.data.totalPages}</span>
                    <button className="btn btn-sm" disabled={page + 1 >= watches.data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
                  </div>
                )}
              </>
            )}
      </section>
    </div>
  );
}
