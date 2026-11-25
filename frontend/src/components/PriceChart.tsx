import { money } from '../utils/format';

export interface Point { t: string; v: number }

/** Lightweight step chart of price history (SVG, no chart library). */
export function PriceChart({ points, currency }: { points: Point[]; currency: string | null }) {
  if (points.length < 2) {
    return <p className="muted small">Price history appears after the price changes at least once.</p>;
  }
  const W = 600, H = 160, P = 8;
  const times = points.map((p) => new Date(p.t).getTime());
  const now = Date.now();
  const t0 = times[0], t1 = Math.max(now, times[times.length - 1]);
  const vals = points.map((p) => p.v);
  const lo = Math.min(...vals), hi = Math.max(...vals);
  const pad = (hi - lo) * 0.15 || hi * 0.05 || 1;
  const y = (v: number) => H - P - ((v - (lo - pad)) / (hi + pad - (lo - pad))) * (H - 2 * P);
  const x = (t: number) => P + ((t - t0) / Math.max(1, t1 - t0)) * (W - 2 * P);
  let d = `M ${x(times[0])} ${y(vals[0])}`;
  for (let i = 1; i < points.length; i++) d += ` H ${x(times[i])} V ${y(vals[i])}`;
  d += ` H ${x(t1)}`;
  const area = `${d} V ${H} H ${x(times[0])} Z`;
  return (
    <figure style={{ margin: 0 }}>
      <svg className="chart" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img"
        aria-label={`Price history from ${money(vals[0], currency)} to ${money(vals[vals.length - 1], currency)}`}>
        <defs>
          <linearGradient id="chartFill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#10b981" stopOpacity="0.28" />
            <stop offset="100%" stopColor="#10b981" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path className="chart-area" d={area} />
        <path className="chart-line" d={d} />
      </svg>
      <figcaption className="row-between tiny muted">
        <span>Low {money(lo, currency)}</span>
        <span>High {money(hi, currency)}</span>
      </figcaption>
    </figure>
  );
}
