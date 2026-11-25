# NextWindow

**Stop checking. Start getting notified.**

NextWindow monitors public web pages and notifies you when something you care about happens: a price drops, reaches
your target, an item comes back in stock, a keyword appears or disappears, or the page meaningfully changes.

- Zero-cost stack: Java 21 / Spring Boot 3, PostgreSQL, React + Vite. No paid APIs, no AI dependency.
- Shared monitoring: one URL is fetched **once** per check no matter how many users watch it.
- Deterministic extraction (JSON-LD, microdata, Open Graph, cautious DOM heuristics) — unknown values stay unknown.
- In-app notification center plus browser Web Push (VAPID, implemented with JDK crypto only).
- Responsible fetching: SSRF protection with DNS pinning, robots.txt, per-host throttling, backoff; never bypasses
  logins, paywalls, CAPTCHAs or bot protection.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/API.md](docs/API.md).

---

## Quick start (Docker Compose)

Requirements: Docker with Compose v2.

```bash
cp .env.example .env            # optional – edit values, add VAPID keys for push
docker compose up --build
```

Open **http://localhost:8081**.

Development defaults enable **demo mode** and seed an account:

| Email | Password |
|---|---|
| `demo@nextwindow.dev` | `demo-pass-2024` |

The demo account already watches deterministic `demo://` sources. Open a watch and use **Demo controls** (drop price,
restock, add keyword, simulate failure…) to see detection, alerts and notifications end to end without touching real
websites.

Services: `frontend` (nginx serving the SPA and proxying `/api`, port 8081), `backend` (port 8080, bound to
localhost), `db` (PostgreSQL 16, bound to localhost).

### Enabling browser push

```bash
node scripts/generate-vapid-keys.mjs >> .env   # or: npx web-push generate-vapid-keys
docker compose up -d --build backend
```

Then in the app: **Settings → Enable push on this device → Send test notification**.
Push needs a secure context (HTTPS, or `localhost`). On iOS the app must be added to the Home Screen first.
Without VAPID keys, everything else works and alerts appear in-app only.

---

## Local development (without Docker for the apps)

Requirements: Java 21, Maven 3.9+, Node 22+, PostgreSQL 16 (or `docker compose up -d db`).

```bash
# Database (user/password/db = nextwindow)
docker compose up -d db

# Backend – http://localhost:8080
cd backend
DEMO_ENABLED=true DEMO_SEED_USER=true mvn spring-boot:run

# Frontend – http://localhost:5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

## Tests

Backend tests include PostgreSQL integration tests (real Flyway migrations, `FOR UPDATE SKIP LOCKED`, etc.). They need a
database `nextwindow_test` reachable at `localhost:5432` (override with `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`,
`TEST_DATABASE_PASSWORD`).

```bash
docker compose up -d db
docker compose exec db createdb -U nextwindow nextwindow_test
cd backend && mvn test              # 105 tests: unit + integration

cd frontend && npm test             # Vitest + Testing Library
npm run build                       # type-check + production build
```

No test contacts real websites: integration tests use the deterministic `MockSourceFetcher`, and the HTTP fetcher is
tested against an in-process loopback server.

## Configuration

All configuration is via environment variables (see `backend/src/main/resources/application.yml`).

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | local `nextwindow` | PostgreSQL connection |
| `PUBLIC_URL` | `http://localhost:5173` | Public app URL used in notification links |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated allowed origins (only needed if the SPA is on another origin) |
| `COOKIE_SECURE` | `false` | Set `true` behind HTTPS |
| `FORWARD_HEADERS_STRATEGY` | `none` | `native` when behind a trusted reverse proxy |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | empty | Web Push; empty = in-app only |
| `MONITORING_DEFAULT_INTERVAL_MINUTES` | `60` | Default check interval |
| `MONITORING_MIN_INTERVAL_MINUTES` | `15` | Lowest interval users may choose for real sites |
| `MONITORING_WORKER_THREADS` | `4` | Concurrent source checks |
| `MONITORING_SCHEDULER_ENABLED` | `true` | Disable to run an API-only instance |
| `FETCH_HOST_MIN_DELAY` | `10s` | Minimum delay between requests to one host |
| `FETCH_USER_AGENT` | `NextWindowBot/1.0 (...)` | Identifies the crawler to site owners |
| `DEMO_ENABLED` / `DEMO_SEED_USER` | `false` | Development demo sources / demo account. **Keep off in production** |

## Deployment

NextWindow is a single backend container + a static frontend + PostgreSQL, so it runs on any VM or container host
(including free tiers).

1. Provision PostgreSQL 14+ and set `DATABASE_*`.
2. Build images: `docker compose build` (or `docker build ./backend`, `docker build ./frontend`).
3. Run the backend with `DEMO_ENABLED=false`, `DEMO_SEED_USER=false`, `COOKIE_SECURE=true`,
   `FORWARD_HEADERS_STRATEGY=native`, `PUBLIC_URL=https://your.domain`, and VAPID keys.
4. Serve the frontend image (nginx) behind HTTPS; set its `BACKEND_URL` env to the backend's internal URL. The API stays
   same-origin under `/api`, so no CORS configuration is needed.
5. Flyway migrates the schema on startup. Health: `GET /actuator/health`.

Scaling: the scheduler claims work with database row leases (`FOR UPDATE SKIP LOCKED` + `locked_until`), so multiple
backend instances can run without double-processing. The in-memory rate limiter and per-host throttle are per instance;
move them to a shared store if you scale horizontally.

## Project layout

```
backend/   Spring Boot modular monolith (auth, user, watch, source, monitoring, extraction,
           condition, notification, scheduler, history, demo, common, config)
frontend/  React + Vite + TypeScript SPA (pages, components, layouts, services, hooks, forms, utils, types)
docs/      Architecture and API documentation
scripts/   Utilities (VAPID key generation)
```
