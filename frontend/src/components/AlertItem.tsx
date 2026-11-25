import { Link } from 'react-router-dom';
import type { Alert } from '../types/api';
import { dateTime, displayUrl, isSafeExternalUrl, relativeTime } from '../utils/format';

const PUSH_LABEL: Record<string, string> = {
  SENT: 'Push delivered to push service',
  PARTIAL: 'Push reached some devices',
  FAILED: 'Push delivery failed',
  NO_SUBSCRIPTIONS: 'No push devices',
  DISABLED: 'Push off',
  NOT_CONFIGURED: 'In-app only',
  PENDING: 'Sending push…',
};

export function AlertItem({ alert, onToggleRead, compact }: { alert: Alert; onToggleRead?: (a: Alert) => void; compact?: boolean }) {
  return (
    <article className="list-item fade-in" aria-label={alert.title}>
      <span className={'dot' + (alert.read ? ' read' : '')} aria-label={alert.read ? 'Read' : 'Unread'} />
      <div className="grow stack-sm" style={{ gap: 4 }}>
        <div className="row-between" style={{ alignItems: 'flex-start' }}>
          <Link to={`/watches/${alert.watchId}`} className="grow" style={{ color: 'var(--text)', fontWeight: alert.read ? 500 : 650 }}>
            {alert.title}
          </Link>
          <time className="tiny muted" dateTime={alert.createdAt} title={dateTime(alert.createdAt)}>{relativeTime(alert.createdAt)}</time>
        </div>
        <p className="text-2 small break">{alert.message}</p>
        {(alert.oldValue || alert.newValue) && !compact && (
          <div className="value-change">
            {alert.oldValue && <span className="old break">{alert.oldValue}</span>}
            {alert.oldValue && alert.newValue && <span className="muted" aria-hidden>→</span>}
            {alert.newValue && <span className="new break">{alert.newValue}</span>}
          </div>
        )}
        {!compact && (
          <div className="row small" style={{ gap: 10 }}>
            {alert.sourceUrl && (isSafeExternalUrl(alert.sourceUrl)
              ? <a href={alert.sourceUrl} target="_blank" rel="noopener noreferrer nofollow" className="truncate" style={{ maxWidth: 260 }}>{displayUrl(alert.sourceUrl)} ↗</a>
              : <span className="muted mono">{alert.sourceUrl}</span>)}
            <span className="tiny muted">{PUSH_LABEL[alert.pushStatus] ?? alert.pushStatus}</span>
            {onToggleRead && (
              <button className="btn btn-ghost btn-sm" style={{ marginLeft: 'auto' }} onClick={() => onToggleRead(alert)}>
                {alert.read ? 'Mark unread' : 'Mark read'}
              </button>
            )}
          </div>
        )}
      </div>
    </article>
  );
}
