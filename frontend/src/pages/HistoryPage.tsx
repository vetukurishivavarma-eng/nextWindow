import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { OutcomeBadge } from '../components/Badges';
import { EmptyState, ErrorState, SkeletonCards } from '../components/States';
import { dateTime, displayUrl, relativeTime } from '../utils/format';

export function HistoryPage() {
  const [filter, setFilter] = useState<'all' | 'changes' | 'failures'>('all');
  const [page, setPage] = useState(0);
  const runs = useAsync(() => api.checks({ filter, page, size: 25 }), [filter, page]);
  const health = useAsync(() => api.health(), []);
  const h = health.data;
  return (
    <div className="stack" style={{ gap: 20 }}>
      <div className="page-head" style={{ marginBottom: 0 }}>
        <div><h1>Monitoring history</h1><p className="muted">Every check NextWindow ran for your pages.</p></div>
      </div>
      {h && (
        <div className="grid-stats">
          <div className="card stat"><div className="stat-value">{h.healthy}</div><div className="stat-label">Healthy sources</div></div>
          <div className="card stat"><div className="stat-value">{h.degraded + h.failing}</div><div className="stat-label">Retrying / failing</div></div>
          <div className="card stat"><div className="stat-value">{h.blocked}</div><div className="stat-label">Access refused</div></div>
          <div className="card stat"><div className="stat-value" style={{ fontSize: '1.1rem', paddingTop: 8 }}>{relativeTime(h.lastCheckAt)}</div><div className="stat-label">Last check</div></div>
        </div>
      )}
      <div className="segmented" role="group" aria-label="Filter history" style={{ alignSelf: 'flex-start' }}>
        {(['all', 'changes', 'failures'] as const).map((f) => (
          <button key={f} aria-pressed={filter === f} onClick={() => { setFilter(f); setPage(0); }}>{f === 'all' ? 'All checks' : f[0].toUpperCase() + f.slice(1)}</button>
        ))}
      </div>
      <section className="card">
        {runs.error ? <ErrorState error={runs.error} onRetry={runs.reload} /> : !runs.data ? <SkeletonCards count={3} />
          : runs.data.items.length === 0 ? <EmptyState icon="history" title="No checks yet">Checks appear here once your watches start being monitored.</EmptyState> : (
            <div className="timeline">
              {runs.data.items.map((c) => (
                <div key={c.id} className="timeline-item">
                  <time className="tiny muted" dateTime={c.startedAt} title={dateTime(c.startedAt)}>{relativeTime(c.startedAt)}</time>
                  <div className="stack-sm" style={{ gap: 4, minWidth: 0 }}>
                    <div className="row" style={{ gap: 8 }}>
                      <OutcomeBadge outcome={c.outcome} />
                      {c.watchId ? <Link to={`/watches/${c.watchId}`} className="truncate" style={{ color: 'var(--text)', fontWeight: 600 }}>{c.watchTitle}</Link> : null}
                    </div>
                    <span className="small text-2 break">{c.message}</span>
                    <span className="tiny muted truncate">{displayUrl(c.sourceUrl)} · {c.durationMs} ms{c.httpStatus ? ` · HTTP ${c.httpStatus}` : ''}{c.alertsCreated ? ` · ${c.alertsCreated} alert(s)` : ''}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        {runs.data && runs.data.totalPages > 1 && (
          <div className="row" style={{ justifyContent: 'center', marginTop: 16 }}>
            <button className="btn btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
            <span className="small muted">Page {page + 1} of {runs.data.totalPages}</span>
            <button className="btn btn-sm" disabled={page + 1 >= runs.data.totalPages} onClick={() => setPage(page + 1)}>Older</button>
          </div>
        )}
      </section>
    </div>
  );
}
