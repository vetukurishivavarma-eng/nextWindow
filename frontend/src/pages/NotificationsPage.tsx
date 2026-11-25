import { useState } from 'react';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useUnread } from '../hooks/useUnread';
import { AlertItem } from '../components/AlertItem';
import { EmptyState, ErrorState, SkeletonCards } from '../components/States';
import { useToast } from '../components/Toast';
import type { Alert } from '../types/api';

export function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const alerts = useAsync(() => api.alerts({ unread: unreadOnly, page, size: 20 }), [unreadOnly, page]);
  const { unread, refresh } = useUnread();
  const toast = useToast();

  const toggle = async (a: Alert) => {
    try {
      const updated = a.read ? await api.markUnread(a.id) : await api.markRead(a.id);
      if (alerts.data) {
        alerts.setData({
          ...alerts.data,
          items: unreadOnly && updated.read ? alerts.data.items.filter((x) => x.id !== a.id) : alerts.data.items.map((x) => (x.id === a.id ? updated : x)),
        });
      }
      refresh();
    } catch {
      toast('Could not update the notification', 'bad');
    }
  };

  const markAll = async () => {
    try {
      const r = await api.markAllRead();
      toast(r.updated ? `Marked ${r.updated} as read` : 'Everything is already read', 'ok');
      refresh();
      void alerts.reload();
    } catch {
      toast('Could not mark notifications as read', 'bad');
    }
  };

  return (
    <div className="stack" style={{ gap: 20, maxWidth: 820, margin: '0 auto' }}>
      <div className="page-head" style={{ marginBottom: 0 }}>
        <div><h1>Notifications</h1><p className="muted">{unread} unread</p></div>
        <button className="btn" onClick={markAll} disabled={unread === 0}>Mark all read</button>
      </div>
      <div className="segmented" role="group" aria-label="Filter notifications" style={{ alignSelf: 'flex-start' }}>
        <button aria-pressed={!unreadOnly} onClick={() => { setUnreadOnly(false); setPage(0); }}>All</button>
        <button aria-pressed={unreadOnly} onClick={() => { setUnreadOnly(true); setPage(0); }}>Unread</button>
      </div>
      <section className="card">
        {alerts.error ? <ErrorState error={alerts.error} onRetry={alerts.reload} /> : !alerts.data ? <SkeletonCards count={3} />
          : alerts.data.items.length === 0 ? (
            <EmptyState icon="bell" title={unreadOnly ? 'You’re all caught up' : 'No notifications yet'}>
              {unreadOnly ? 'No unread notifications.' : 'When a watch condition is met, it shows up here — and as a push notification if enabled.'}
            </EmptyState>
          ) : <div className="list">{alerts.data.items.map((a) => <AlertItem key={a.id} alert={a} onToggleRead={toggle} />)}</div>}
        {alerts.data && alerts.data.totalPages > 1 && (
          <div className="row" style={{ justifyContent: 'center', marginTop: 16 }}>
            <button className="btn btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
            <span className="small muted">Page {page + 1} of {alerts.data.totalPages}</span>
            <button className="btn btn-sm" disabled={page + 1 >= alerts.data.totalPages} onClick={() => setPage(page + 1)}>Older</button>
          </div>
        )}
      </section>
    </div>
  );
}
