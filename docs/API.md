# REST API

Base path `/api`. JSON in/out. Authentication uses the `nw_session` HttpOnly cookie set by register/login.
All non-GET requests must send `X-Requested-With: NextWindow` and `Content-Type: application/json`.

Errors:

```json
{ "status": 400, "code": "VALIDATION_FAILED", "message": "Please correct the highlighted fields.",
  "fieldErrors": { "url": "Enter the URL to monitor" }, "path": "/api/watches", "timestamp": "…" }
```

Common codes: `UNAUTHENTICATED` (401), `CSRF_REJECTED` (403), `NOT_FOUND` (404 – also for other users' resources),
`URL_REJECTED`, `VALIDATION_FAILED` (400), `EMAIL_TAKEN`, `WATCH_LIMIT` (409), `RATE_LIMITED` (429),
`PUSH_NOT_CONFIGURED` (503).

Paged responses: `{ "items": [], "page": 0, "size": 20, "totalItems": 0, "totalPages": 0 }` (`size` ≤ 100).

## Public

| Method | Path | Description |
|---|---|---|
| GET | `/api/config` | `{demoEnabled, pushConfigured, vapidPublicKey, intervalOptions, defaultIntervalMinutes}` |
| GET | `/actuator/health` | Health probe |

## Authentication

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | `{email, password}` | 201 + cookie. Password ≥ 8 chars incl. a non-letter |
| POST | `/api/auth/login` | `{email, password}` | 200 + cookie |
| POST | `/api/auth/logout` | – | 204, clears cookie |
| GET | `/api/auth/me` | – | Current user |

Sessions slide automatically (14 days); no refresh token is needed.

## Sources

| Method | Path | Description |
|---|---|---|
| POST | `/api/sources/analyze` | `{url}` → detected title/price/currency/availability, notes, and per-watch-type support. Reuses a recent shared snapshot when available |
| GET | `/api/sources/{id}` | Source status (only if you watch it) |

## Watches

| Method | Path | Description |
|---|---|---|
| POST | `/api/watches` | Create. Body below |
| GET | `/api/watches?filter=all\|active\|paused&q=&page=&size=` | List |
| GET | `/api/watches/{id}` | Detail incl. source health, current and previous state |
| PUT | `/api/watches/{id}` | Edit (title, condition, mode, cooldown, interval, expiry) |
| POST | `/api/watches/{id}/pause` · `/resume` | Pause / resume |
| DELETE | `/api/watches/{id}` | Delete |
| POST | `/api/watches/{id}/check-now` | Immediate check (rate-limited, min 2 min between checks of a real site) |
| GET | `/api/watches/{id}/snapshots` | Price/availability history (paged) |
| GET | `/api/watches/{id}/checks` | Check history (paged) |

```json
{
  "url": "https://shop.example.com/item/42",
  "title": "Desk lamp",
  "watchType": "PRICE_BELOW",
  "targetPrice": 29.99,
  "targetKeyword": null,
  "alertMode": "REPEAT",
  "cooldownMinutes": 60,
  "checkIntervalMinutes": 60,
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

`watchType`: `ANY_CHANGE`, `PRICE_DROP`, `PRICE_BELOW` (needs `targetPrice`), `STOCK_AVAILABLE`, `KEYWORD_FOUND` /
`KEYWORD_REMOVED` (need `targetKeyword`), `STATUS_CHANGE`. `alertMode`: `ONE_TIME` | `REPEAT`.
`checkIntervalMinutes` ∈ {15, 30, 60, 180, 360, 720, 1440}.

## Alerts

| Method | Path | Description |
|---|---|---|
| GET | `/api/alerts?unread=&watchId=&page=&size=` | List |
| GET | `/api/alerts/unread` | Unread only |
| GET | `/api/alerts/unread-count` | `{unread}` |
| POST | `/api/alerts/{id}/read` · `/unread` | Change read state |
| POST | `/api/alerts/read-all` | `{updated}` |

## Monitoring & dashboard

| Method | Path | Description |
|---|---|---|
| GET | `/api/monitoring/health` | Source counts by status, last/next check, items needing attention |
| GET | `/api/monitoring/checks?filter=all\|changes\|failures` | Check history across your watches |
| GET | `/api/dashboard` | Counts, recent alerts, recent changes, health |

## Push

| Method | Path | Description |
|---|---|---|
| GET | `/api/push/subscriptions` | Your devices |
| POST | `/api/push/subscribe` | `{endpoint, keys:{p256dh, auth}}` (browser `PushSubscription.toJSON()`) |
| POST | `/api/push/unsubscribe` | `{endpoint}` |
| DELETE | `/api/push/subscriptions/{id}` | Remove a device |
| POST | `/api/push/test` | Sends a test; returns `{devices, delivered, failed, removed, message}` |

## Settings & account

| Method | Path | Description |
|---|---|---|
| GET / PUT | `/api/settings` | `{pushEnabled}` |
| POST | `/api/account/password` | `{currentPassword, newPassword}`; signs out other sessions |
| DELETE | `/api/account` | `{password}` |

## Demo (only when `DEMO_ENABLED=true`)

| Method | Path | Description |
|---|---|---|
| GET | `/api/demo/sources` | Demo sources (`demo://headphones`, `sneakers`, `console`, `careers`, `transit`) |
| POST | `/api/demo/sources/{slug}/simulate` | `{action, keyword?, checkNow?}` – actions: `PRICE_DROP`, `PRICE_INCREASE`, `RESTOCK`, `SELL_OUT`, `ADD_KEYWORD`, `REMOVE_KEYWORD`, `PAGE_CHANGE`, `FAIL_NEXT`, `RESET`. Runs a check immediately if you watch that source |
