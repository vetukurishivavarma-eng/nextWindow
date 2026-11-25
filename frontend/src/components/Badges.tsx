import { availabilityLabel, outcomeInfo, statusInfo } from '../utils/format';
import type { Availability } from '../types/api';

export function StatusBadge({ status }: { status: string }) {
  const s = statusInfo(status);
  return <span className={`badge tone-${s.tone}`}>{s.label}</span>;
}

export function OutcomeBadge({ outcome }: { outcome: string }) {
  const o = outcomeInfo(outcome);
  return <span className={`badge tone-${o.tone}`}>{o.label}</span>;
}

export function AvailabilityBadge({ value }: { value: Availability | null | undefined }) {
  const tone = value === 'IN_STOCK' || value === 'LIMITED' ? 'ok' : value === 'OUT_OF_STOCK' || value === 'DISCONTINUED' ? 'bad'
    : value === 'UNKNOWN' || !value ? 'muted' : 'warn';
  return <span className={`badge tone-${tone}`}>{availabilityLabel(value)}</span>;
}
