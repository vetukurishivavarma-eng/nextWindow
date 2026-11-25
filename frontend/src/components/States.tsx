import type { ReactNode } from 'react';
import { ApiError } from '../services/api';
import { Icon } from './Icon';

export function Spinner({ large, label = 'Loading' }: { large?: boolean; label?: string }) {
  return <span className={'spinner' + (large ? ' spinner-lg' : '')} role="status" aria-label={label} />;
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="center" aria-live="polite">
      <div className="row"><Spinner large /><span className="muted">{label}</span></div>
    </div>
  );
}

export function SkeletonCards({ count = 3 }: { count?: number }) {
  return (
    <div className="grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => <div key={i} className="skeleton" style={{ height: 120 }} />)}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError || error instanceof Error ? error.message : 'Something went wrong.';
  return (
    <div className="notice notice-bad" role="alert">
      <Icon name="alert" />
      <div className="grow stack-sm">
        <span>{message}</span>
        {onRetry && <div><button className="btn btn-sm" onClick={onRetry}>Try again</button></div>}
      </div>
    </div>
  );
}

export function EmptyState({ icon = 'search', title, children, action }: { icon?: string; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-icon"><Icon name={icon} /></div>
      <h3>{title}</h3>
      {children && <p className="muted small" style={{ maxWidth: 420 }}>{children}</p>}
      {action}
    </div>
  );
}
