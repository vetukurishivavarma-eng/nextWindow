import { Link } from 'react-router-dom';
import type { Watch } from '../types/api';
import { displayUrl, money, relativeTime, WATCH_TYPES } from '../utils/format';
import { AvailabilityBadge, StatusBadge } from './Badges';

export function WatchCard({ watch }: { watch: Watch }) {
  const c = watch.current;
  const statusBadge = !watch.active ? (watch.completedAt ? 'COMPLETED' : null) : watch.source.status;
  return (
    <Link to={`/watches/${watch.id}`} className="card card-link watch-card fade-in" aria-label={`Open watch ${watch.title}`}>
      <div className="row-between" style={{ alignItems: 'flex-start' }}>
        <div className="grow">
          <div className="watch-title truncate">{watch.title}</div>
          <div className="muted small truncate">{displayUrl(watch.source.url)}</div>
        </div>
        {watch.unreadAlerts > 0 && <span className="nav-badge" aria-label={`${watch.unreadAlerts} unread alerts`}>{watch.unreadAlerts}</span>}
      </div>
      <div className="row-between">
        <div>
          {c?.price != null ? <div className="price-big">{money(c.price, c.currency)}</div>
            : <div className="text-2 small">{watch.conditionState}</div>}
        </div>
        {c && c.availability !== 'UNKNOWN' && <AvailabilityBadge value={c.availability} />}
      </div>
      <div className="watch-meta">
        <span className="badge no-dot">{WATCH_TYPES[watch.watchType].short}
          {watch.watchType === 'PRICE_BELOW' && watch.targetPrice != null && ` ≤ ${money(watch.targetPrice, c?.currency)}`}
          {watch.targetKeyword && ` “${watch.targetKeyword}”`}
        </span>
        {!watch.active && !watch.completedAt && <span className="badge tone-muted">Paused</span>}
        {statusBadge && <StatusBadge status={statusBadge} />}
        {watch.conditionMet && watch.active && <span className="badge tone-ok">Condition met</span>}
        <span className="tiny muted" style={{ marginLeft: 'auto' }}>Checked {relativeTime(watch.source.lastFetchedAt)}</span>
      </div>
    </Link>
  );
}
