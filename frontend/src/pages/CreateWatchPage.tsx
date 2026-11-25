import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../services/api';
import { useAuth } from '../hooks/useAuth';
import type { Analysis, DemoSource } from '../types/api';
import { WatchConditionFields } from '../forms/WatchConditionFields';
import { defaultForm, toInput, validateForm, type WatchFormState } from '../forms/watchForm';
import { AvailabilityBadge } from '../components/Badges';
import { Spinner } from '../components/States';
import { Icon } from '../components/Icon';
import { useToast } from '../components/Toast';
import { availabilityLabel, money } from '../utils/format';

const SOURCE_LABEL: Record<string, string> = {
  STRUCTURED_DATA: 'from product data', MICRODATA: 'from product markup', METADATA: 'from page metadata', HEURISTIC: 'estimated from layout',
};

export function CreateWatchPage() {
  const { config } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [url, setUrl] = useState(params.get('url') ?? '');
  const [analysis, setAnalysis] = useState<Analysis>();
  const [analyzing, setAnalyzing] = useState(false);
  const [analyzeError, setAnalyzeError] = useState<string>();
  const [form, setForm] = useState<WatchFormState>(defaultForm(config?.defaultIntervalMinutes ?? 60));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [demo, setDemo] = useState<DemoSource[]>([]);

  useEffect(() => {
    if (config?.demoEnabled) api.demoSources().then(setDemo).catch(() => setDemo([]));
  }, [config?.demoEnabled]);

  const analyze = async (target = url) => {
    if (!target.trim()) return;
    setAnalyzing(true);
    setAnalyzeError(undefined);
    setAnalysis(undefined);
    try {
      const a = await api.analyze(target.trim());
      setAnalysis(a);
      const rec = a.watchTypes.find((t) => t.recommended)?.type ?? 'ANY_CHANGE';
      setForm((f) => ({ ...f, watchType: rec, title: a.title ?? '' }));
    } catch (e) {
      setAnalyzeError(e instanceof ApiError ? e.message : 'Could not analyze this URL.');
    } finally {
      setAnalyzing(false);
    }
  };

  useEffect(() => {
    if (params.get('url')) void analyze(params.get('url')!);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onAnalyze = (e: FormEvent) => {
    e.preventDefault();
    void analyze();
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const v = validateForm(form, false);
    setErrors(v);
    setSubmitError(undefined);
    if (Object.keys(v).length || !analysis) return;
    setSaving(true);
    try {
      const w = await api.createWatch({ ...toInput(form), url: analysis.canonicalUrl });
      toast('Watch created. We’ll notify you when something changes.', 'ok');
      navigate(`/watches/${w.id}`);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        setSubmitError(err.message);
      } else setSubmitError('Could not create the watch.');
    } finally {
      setSaving(false);
    }
  };

  const canCreate = analysis && analysis.watchTypes.length > 0;
  return (
    <div className="stack" style={{ maxWidth: 760, margin: '0 auto', gap: 20 }}>
      <div className="page-head" style={{ marginBottom: 0 }}>
        <div><h1>New watch</h1><p className="muted">Paste a link. We’ll check what can be monitored.</p></div>
      </div>

      <form className="card stack" onSubmit={onAnalyze} aria-label="Analyze URL">
        <div className="field">
          <label className="label" htmlFor="url">Page URL</label>
          <div className="input-group">
            <input id="url" className="input" inputMode="url" placeholder="https://shop.example.com/product/123" value={url}
              onChange={(e) => setUrl(e.target.value)} autoFocus={!url} />
            <button className="btn btn-primary" disabled={analyzing || !url.trim()}>
              {analyzing ? <><Spinner /> Analyzing…</> : 'Analyze'}
            </button>
          </div>
          <span className="hint">Public pages only. NextWindow respects robots.txt and never bypasses logins, paywalls or CAPTCHAs.</span>
        </div>
        {analyzeError && <div className="notice notice-bad small" role="alert">{analyzeError}</div>}
        {demo.length > 0 && !analysis && (
          <div className="stack-sm">
            <span className="label row" style={{ gap: 6 }}><Icon name="flask" /> Demo sources (development)</span>
            <div className="row" style={{ gap: 8 }}>
              {demo.map((d) => (
                <button key={d.slug} type="button" className="btn btn-sm" title={d.description ?? undefined}
                  onClick={() => { setUrl(d.url); void analyze(d.url); }}>{d.title}</button>
              ))}
            </div>
          </div>
        )}
      </form>

      {analyzing && <div className="card"><div className="skeleton" style={{ height: 90 }} /></div>}

      {analysis && (
        <section className="card stack fade-in" aria-labelledby="detected-h">
          <div className="row-between">
            <h2 id="detected-h">{analysis.reachable ? 'What we found' : 'We couldn’t read this page'}</h2>
            {analysis.fromSharedSource && <span className="badge tone-accent">Already monitored — shared</span>}
          </div>
          {analysis.problem && <div className="notice notice-warn small">{analysis.problem}</div>}
          {analysis.reachable && (
            <dl className="kv">
              <dt>Title</dt><dd>{analysis.title ?? <span className="muted">Not detected</span>}</dd>
              <dt>Price</dt>
              <dd>{analysis.price != null
                ? <>{money(analysis.price, analysis.currency)} <span className="tiny muted">{SOURCE_LABEL[analysis.priceSource ?? ''] ?? ''}</span></>
                : <span className="muted">Not detected</span>}</dd>
              <dt>Availability</dt>
              <dd>{analysis.availability !== 'UNKNOWN' ? <AvailabilityBadge value={analysis.availability} /> : <span className="muted">{availabilityLabel('UNKNOWN')}</span>}</dd>
              <dt>Address</dt><dd className="mono small">{analysis.canonicalUrl}</dd>
            </dl>
          )}
          {analysis.textPreview && <p className="small muted break" style={{ borderLeft: '2px solid var(--border-strong)', paddingLeft: 10 }}>{analysis.textPreview}</p>}
          {analysis.notes.map((n) => <div key={n} className="notice notice-info small">{n}</div>)}
          {!canCreate && <p className="small">This page can’t be monitored. Try another address.</p>}
        </section>
      )}

      {canCreate && (
        <form className="card stack fade-in" onSubmit={submit} aria-label="Watch settings" noValidate>
          <div className="field">
            <label className="label" htmlFor="title">Name</label>
            <input id="title" className="input" value={form.title} maxLength={200} placeholder={analysis!.title ?? 'My watch'}
              onChange={(e) => setForm({ ...form, title: e.target.value })} aria-invalid={!!errors.title} />
            {errors.title && <span className="field-error">{errors.title}</span>}
          </div>
          <WatchConditionFields value={form} onChange={setForm} errors={errors} support={analysis!.watchTypes}
            currency={analysis!.currency} intervals={config?.intervalOptions ?? [15, 30, 60, 180, 360, 720, 1440]} />
          {form.watchType === 'PRICE_BELOW' && analysis!.price != null && Number(form.targetPrice) >= analysis!.price && (
            <div className="notice notice-info small">The current price ({money(analysis!.price, analysis!.currency)}) already meets this target. You’ll be alerted the next time it crosses below after rising.</div>
          )}
          {submitError && <div className="notice notice-bad small" role="alert">{submitError}</div>}
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <Link to="/dashboard" className="btn btn-ghost">Cancel</Link>
            <button className="btn btn-primary" disabled={saving}>{saving ? 'Creating…' : 'Create watch'}</button>
          </div>
        </form>
      )}
    </div>
  );
}
