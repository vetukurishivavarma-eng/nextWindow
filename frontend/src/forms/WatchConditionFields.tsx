import type { TypeSupport, WatchType } from '../types/api';
import { interval, WATCH_TYPES } from '../utils/format';
import type { WatchFormState } from './watchForm';

const ORDER: WatchType[] = ['PRICE_DROP', 'PRICE_BELOW', 'STOCK_AVAILABLE', 'STATUS_CHANGE', 'KEYWORD_FOUND', 'KEYWORD_REMOVED', 'ANY_CHANGE'];
const COOLDOWNS = [0, 15, 60, 360, 1440];

export function WatchConditionFields({ value, onChange, errors, support, currency, intervals }: {
  value: WatchFormState;
  onChange: (v: WatchFormState) => void;
  errors: Record<string, string>;
  support?: TypeSupport[];
  currency?: string | null;
  intervals: number[];
}) {
  const set = <K extends keyof WatchFormState>(k: K, v: WatchFormState[K]) => onChange({ ...value, [k]: v });
  const supportFor = (t: WatchType) => support?.find((s) => s.type === t);
  const types = support && support.length ? ORDER.filter((t) => supportFor(t)) : ORDER;
  const selectedNote = supportFor(value.watchType);

  return (
    <div className="stack">
      <fieldset className="field" style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="label" style={{ marginBottom: 8 }}>Notify me when…</legend>
        <div className="choice-grid" role="radiogroup" aria-label="Watch condition">
          {types.map((t) => {
            const s = supportFor(t);
            return (
              <label key={t} className="choice" role="radio" aria-checked={value.watchType === t} tabIndex={-1}>
                <input type="radio" name="watchType" value={t} checked={value.watchType === t} onChange={() => set('watchType', t)} />
                <span className="choice-dot" aria-hidden />
                <span className="stack-sm" style={{ gap: 2 }}>
                  <span style={{ fontWeight: 600 }}>
                    {WATCH_TYPES[t].label}{' '}
                    {s?.recommended && <span className="badge tone-ok no-dot" style={{ marginLeft: 4 }}>Recommended</span>}
                  </span>
                  <span className="muted small">{WATCH_TYPES[t].description}</span>
                </span>
              </label>
            );
          })}
        </div>
        {selectedNote?.note && <div className="notice notice-warn small" style={{ marginTop: 10 }}>{selectedNote.note}</div>}
      </fieldset>

      {value.watchType === 'PRICE_BELOW' && (
        <div className="field">
          <label className="label" htmlFor="targetPrice">Target price{currency ? ` (${currency})` : ''}</label>
          <input id="targetPrice" className="input" inputMode="decimal" placeholder="e.g. 149.99" value={value.targetPrice}
            aria-invalid={!!errors.targetPrice} aria-describedby="targetPrice-err" onChange={(e) => set('targetPrice', e.target.value)} />
          {errors.targetPrice && <span id="targetPrice-err" className="field-error">{errors.targetPrice}</span>}
        </div>
      )}
      {(value.watchType === 'KEYWORD_FOUND' || value.watchType === 'KEYWORD_REMOVED') && (
        <div className="field">
          <label className="label" htmlFor="targetKeyword">Keyword or phrase</label>
          <input id="targetKeyword" className="input" placeholder="e.g. Senior Java Engineer" value={value.targetKeyword} maxLength={200}
            aria-invalid={!!errors.targetKeyword} aria-describedby="kw-hint kw-err" onChange={(e) => set('targetKeyword', e.target.value)} />
          <span id="kw-hint" className="hint">Matching ignores capitalization and extra spaces.</span>
          {errors.targetKeyword && <span id="kw-err" className="field-error">{errors.targetKeyword}</span>}
        </div>
      )}

      <div className="grid-2">
        <div className="field">
          <span className="label" id="mode-label">Alert mode</span>
          <div className="segmented" role="group" aria-labelledby="mode-label">
            <button type="button" aria-pressed={value.alertMode === 'REPEAT'} onClick={() => set('alertMode', 'REPEAT')}>Every time</button>
            <button type="button" aria-pressed={value.alertMode === 'ONE_TIME'} onClick={() => set('alertMode', 'ONE_TIME')}>Once, then stop</button>
          </div>
        </div>
        <div className="field">
          <label className="label" htmlFor="interval">Check frequency</label>
          <select id="interval" className="select" value={value.checkIntervalMinutes} onChange={(e) => set('checkIntervalMinutes', Number(e.target.value))}>
            {intervals.map((i) => <option key={i} value={i}>{interval(i).replace(/^./, (c) => c.toUpperCase())}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="cooldown">Minimum time between alerts</label>
          <select id="cooldown" className="select" value={value.cooldownMinutes} onChange={(e) => set('cooldownMinutes', Number(e.target.value))}>
            {COOLDOWNS.map((c) => <option key={c} value={c}>{c === 0 ? 'No cooldown' : c < 60 ? `${c} minutes` : c < 1440 ? `${c / 60} hour${c > 60 ? 's' : ''}` : '1 day'}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="label" htmlFor="expires">Stop watching after (optional)</label>
          <input id="expires" type="date" className="input" value={value.expiresAt} aria-invalid={!!errors.expiresAt}
            onChange={(e) => set('expiresAt', e.target.value)} />
          {errors.expiresAt && <span className="field-error">{errors.expiresAt}</span>}
        </div>
      </div>
    </div>
  );
}
