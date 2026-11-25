import type { Availability, SourceStatus, WatchType } from '../types/api';

export function money(amount: number | null | undefined, currency?: string | null): string {
  if (amount === null || amount === undefined) return '—';
  if (currency) {
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
    } catch {
      /* unknown currency code */
    }
  }
  return amount.toFixed(2);
}

export function relativeTime(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return 'never';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '—';
  const diff = Math.round((t - now) / 1000);
  const abs = Math.abs(diff);
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  if (abs < 45) return 'just now';
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
  if (abs < 86400 * 30) return rtf.format(Math.round(diff / 86400), 'day');
  return new Date(iso).toLocaleDateString();
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function interval(minutes: number): string {
  if (minutes < 60) return `every ${minutes} min`;
  if (minutes === 60) return 'hourly';
  if (minutes < 1440) return `every ${minutes / 60} hours`;
  return 'daily';
}

export const WATCH_TYPES: Record<WatchType, { label: string; short: string; description: string }> = {
  PRICE_DROP: { label: 'Price drops', short: 'Price drop', description: 'Alert whenever the price goes down.' },
  PRICE_BELOW: { label: 'Price at or below target', short: 'Target price', description: 'Alert when the price reaches your target.' },
  STOCK_AVAILABLE: { label: 'Back in stock', short: 'Restock', description: 'Alert when an unavailable item becomes available.' },
  STATUS_CHANGE: { label: 'Availability changes', short: 'Status', description: 'Alert on any change in stock status.' },
  KEYWORD_FOUND: { label: 'Keyword appears', short: 'Keyword found', description: 'Alert when a word or phrase shows up on the page.' },
  KEYWORD_REMOVED: { label: 'Keyword disappears', short: 'Keyword gone', description: 'Alert when a word or phrase is no longer on the page.' },
  ANY_CHANGE: { label: 'Any meaningful change', short: 'Any change', description: 'Alert when the page content changes (ignoring noise like timestamps).' },
};

export function availabilityLabel(a: Availability | null | undefined): string {
  switch (a) {
    case 'IN_STOCK': return 'In stock';
    case 'OUT_OF_STOCK': return 'Out of stock';
    case 'PREORDER': return 'Pre-order';
    case 'BACKORDER': return 'Backorder';
    case 'LIMITED': return 'Limited';
    case 'DISCONTINUED': return 'Discontinued';
    default: return 'Unknown';
  }
}

export function statusInfo(s: SourceStatus | string): { label: string; tone: 'ok' | 'warn' | 'bad' | 'muted' } {
  switch (s) {
    case 'ACTIVE': return { label: 'Healthy', tone: 'ok' };
    case 'PENDING': return { label: 'Waiting for first check', tone: 'muted' };
    case 'DEGRADED': return { label: 'Retrying', tone: 'warn' };
    case 'FAILING': return { label: 'Failing', tone: 'bad' };
    case 'BLOCKED': return { label: 'Access refused', tone: 'bad' };
    case 'DISABLED': return { label: 'Disabled', tone: 'muted' };
    case 'COMPLETED': return { label: 'Completed', tone: 'muted' };
    default: return { label: s, tone: 'muted' };
  }
}

export function outcomeInfo(o: string): { label: string; tone: 'ok' | 'warn' | 'bad' | 'muted' | 'accent' } {
  switch (o) {
    case 'BASELINE': return { label: 'Baseline', tone: 'muted' };
    case 'CHANGED': return { label: 'Changed', tone: 'accent' };
    case 'UNCHANGED': return { label: 'No change', tone: 'ok' };
    case 'FAILED': return { label: 'Failed', tone: 'bad' };
    case 'BLOCKED': return { label: 'Blocked', tone: 'bad' };
    case 'RATE_LIMITED': return { label: 'Rate limited', tone: 'warn' };
    default: return { label: o, tone: 'muted' };
  }
}

export function displayUrl(url: string): string {
  if (url.startsWith('demo://')) return url;
  try {
    const u = new URL(url);
    return u.host + (u.pathname === '/' ? '' : u.pathname) + u.search;
  } catch {
    return url;
  }
}

export function isSafeExternalUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  try {
    const u = new URL(url);
    return u.protocol === 'https:' || u.protocol === 'http:';
  } catch {
    return false;
  }
}
