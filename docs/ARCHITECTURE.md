# Architecture

NextWindow is a **modular monolith**: one Spring Boot service, one PostgreSQL database, one static SPA.

```
Browser (React SPA, service worker) ──/api──► nginx ──► Spring Boot ──► PostgreSQL
                                                            │
                                    Scheduler ─► MonitoringService ─► SourceFetcher (static HTTP | demo)
                                                            │                 │
                                                 ExtractionService ◄──────────┘
                                                            │
                                     ConditionEngine ─► AlertService ─► PushDispatcher ─► Web Push services
```

## Backend packages

| Package | Responsibility |
|---|---|
| `auth` | Registration/login, opaque server-side sessions (SHA-256 hashed tokens, HttpOnly SameSite cookie), CSRF header filter |
| `user` | User entity, settings, password change, account deletion |
| `source` | `MonitoringSource`, URL normalization, SSRF validation, race-safe source registration, URL analysis |
| `watch` | User watches (condition config), CRUD, pause/resume, ownership enforcement |
| `monitoring` | The check pipeline, fetchers (`monitoring.fetch`), monitoring health/history queries, dashboard |
| `extraction` | Layered, deterministic extraction of title/price/currency/availability/text |
| `condition` | Pure evaluators for each watch type + change summaries |
| `notification` | Alerts with deduplication, notification center, Web Push (RFC 8291/8292) |
| `scheduler` | Polling scheduler and maintenance jobs (retention, expiry) |
| `history` | Snapshots and check runs |
| `demo` | Dev-only deterministic `demo://` sources and simulation API |
| `common`, `config` | Errors, pagination, rate limiting, properties, security, executors |

## Shared monitoring

`ONE SOURCE → ONE MONITORING PROCESS → MANY WATCHES → MANY USERS`

* A submitted URL is normalized (scheme/host lower-cased, default port, dot-segments, fragment dropped, tracking and
  session parameters such as `utm_*`, `gclid`, `fbclid`, `jsessionid` removed; all other query parameters kept and
  stably sorted). The canonical URL is unique; registration uses `INSERT … ON CONFLICT DO NOTHING`.
* A source's check interval is the most frequent interval requested by its active watches (floored at the configured
  minimum).
* Sources with no active watches are never claimed; deleting the last watch deletes the source and its history.

## Scheduling and locking

Every 15 s the scheduler atomically claims due sources:

```sql
UPDATE monitoring_source SET locked_by = :worker, locked_until = now + 5m
WHERE id IN (SELECT … WHERE next_fetch_at <= now AND (locked_until IS NULL OR locked_until < now)
             AND EXISTS (active watch) ORDER BY next_fetch_at LIMIT n FOR UPDATE SKIP LOCKED)
RETURNING id
```

Claimed IDs run on a bounded thread pool; the lease is always released afterwards and expires on its own if a worker
dies. Manual "check now" uses the same lease (`tryLock`), so a source is never processed concurrently. This is safe for
multiple instances.

## Check pipeline (`MonitoringService`)

1. Fetch **outside** any DB transaction.
2. Extract and compute `contentHash = sha256(title | price | currency | availability | textHash)`.
3. In one transaction: compare with the latest snapshot; store a new snapshot only for a baseline or a change; evaluate
   every active watch of the source; raise alerts; record the check run; update health and `next_fetch_at`
   (interval + up to 5 % jitter).
4. Failures increment `failure_count`, set `DEGRADED` → `FAILING` (after 5), and back off exponentially
   (`interval × 2^(n-1)`, max 24 h). `BLOCKED` (robots.txt / 401 / 403) retries daily. HTTP 429 honours `Retry-After`.
   Users get one "Monitoring problem" alert when a source becomes FAILING or BLOCKED.

## Fetching safely (`GenericStaticPageFetcher`)

* Apache HttpClient with a custom DNS resolver: every resolved address must be public; the validated addresses are the
  exact ones connected to (defeats DNS rebinding). Blocks loopback, RFC 1918, link-local/metadata (169.254.0.0/16),
  CGNAT, multicast, reserved, IPv6 ULA/link-local, IPv4-mapped/NAT64/6to4 embeddings, non-canonical numeric hosts,
  internal suffixes (`.local`, `.internal`, …) and non-web ports.
* Redirects are followed manually (max 5) and every hop is re-validated and robots-checked.
* Timeouts (connect 5 s, read 10 s, overall body deadline), 2 MB body cap, content-type allow-list, no cookies, no
  auth, identifying User-Agent, robots.txt (RFC 9309, cached 6 h, unreachable ⇒ disallow), crawl-delay and per-host
  minimum delay.
* `SourceFetcher` is an interface keyed by `FetchMethod`; authorized API integrations can be added as new fetchers.

## Extraction layers

1. JSON-LD `Product`/`Offer` (incl. `@graph`, type arrays, `AggregateOffer`, `priceSpecification`)
2. schema.org microdata
3. Open Graph / product meta tags
4. Conservative DOM heuristics (single unambiguous price in price-labelled leaf elements, excluding struck/old prices;
   stock from enabled/disabled buy buttons and stock-labelled elements) – marked `HEURISTIC`
5. Normalized visible text for keyword/any-change monitoring

Several conflicting prices ⇒ price is `null` with an explanatory note. For hashing, volatile tokens (clock times,
ISO timestamps, "x minutes ago", UUIDs, long random IDs, epoch numbers) are removed.

## Conditions and deduplication

Each watch stores `last_observed_value`, a condition-specific state. Evaluators are pure functions
`(criteria, previous, observation) → (newState, trigger?)`:

| Type | State | Triggers when |
|---|---|---|
| PRICE_DROP | `price currency` | price decreases (same currency) |
| PRICE_BELOW | `MET:x` / `NOT_MET:x` | NOT_MET → MET (price ≤ target) |
| STOCK_AVAILABLE | availability | known non-purchasable → purchasable |
| STATUS_CHANGE | availability | known → different known |
| KEYWORD_FOUND / REMOVED | PRESENT / ABSENT | ABSENT→PRESENT / PRESENT→ABSENT |
| ANY_CHANGE | content hash | hash differs |

A `null` previous state is a **baseline**: it never triggers. New watches on an existing source get their baseline from
the latest snapshot; changing a watch's condition re-baselines. Unknown values keep the previous state.

`AlertService` suppresses duplicates (same fingerprint as the last alert or within 7 days), enforces per-watch
cooldowns (a suppressed event keeps the old state so it is delivered after the cooldown), completes ONE_TIME watches and
skips inactive watches.

## Notifications

Alerts are stored first; after commit an async listener sends Web Push to each device. The alert records an honest
`push_status` (`SENT`, `PARTIAL`, `FAILED`, `NO_SUBSCRIPTIONS`, `DISABLED`, `NOT_CONFIGURED`). 404/410 from a push
service deletes the subscription; repeated failures remove it after 5 attempts. Push endpoints must belong to known
browser push services (prevents SSRF via subscriptions).

## Security summary

* BCrypt (cost 12); constant-time-ish login for unknown users; login/register/sensitive endpoints rate-limited.
* Opaque 256-bit session tokens, only hashes stored, HttpOnly + SameSite cookies, sliding expiry, max 10 sessions per
  user, other sessions revoked on password change.
* CSRF: state-changing `/api` requests require `X-Requested-With: NextWindow` (unsettable cross-site without a CORS
  preflight that the allow-list rejects) in addition to SameSite cookies.
* Every user-scoped query filters by owner; foreign IDs return 404.
* DTOs only; centralized error handling never leaks stack traces; secrets only from environment; logs never contain
  passwords, tokens or push keys.
* nginx adds CSP, `X-Frame-Options`, `nosniff`, referrer policy.

## Data model

`app_user`, `auth_session`, `monitoring_source`, `watch`, `snapshot`, `check_run`, `alert`, `push_subscription`,
`demo_page` (dev only) — see `backend/src/main/resources/db/migration/V1__initial_schema.sql` for constraints and
indexes. Retention: check runs 30 days, 300 snapshots per source (daily job).
