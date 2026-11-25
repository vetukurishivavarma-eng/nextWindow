-- NextWindow initial schema

CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    push_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

-- Opaque, server-side sessions. Only a SHA-256 hash of the token is stored.
CREATE TABLE auth_session (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64)     NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_auth_session_token ON auth_session (token_hash);
CREATE INDEX ix_auth_session_user ON auth_session (user_id);
CREATE INDEX ix_auth_session_expires ON auth_session (expires_at);

-- One row per distinct canonical URL; shared by every watch on that URL.
CREATE TABLE monitoring_source (
    id                      BIGSERIAL PRIMARY KEY,
    canonical_url           VARCHAR(2048) NOT NULL,
    host                    VARCHAR(255)  NOT NULL,
    source_type             VARCHAR(32)   NOT NULL,
    fetch_method            VARCHAR(32)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    status_message          VARCHAR(500),
    last_content_hash       VARCHAR(64),
    last_fetched_at         TIMESTAMPTZ,
    last_success_at         TIMESTAMPTZ,
    next_fetch_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    check_interval_minutes  INTEGER       NOT NULL DEFAULT 60,
    failure_count           INTEGER       NOT NULL DEFAULT 0,
    locked_by               VARCHAR(100),
    locked_until            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_source_interval CHECK (check_interval_minutes > 0),
    CONSTRAINT ck_source_failures CHECK (failure_count >= 0)
);
CREATE UNIQUE INDEX ux_source_canonical_url ON monitoring_source (canonical_url);
CREATE INDEX ix_source_due ON monitoring_source (next_fetch_at) WHERE status <> 'DISABLED';

CREATE TABLE snapshot (
    id               BIGSERIAL PRIMARY KEY,
    source_id        BIGINT        NOT NULL REFERENCES monitoring_source (id) ON DELETE CASCADE,
    content_hash     VARCHAR(64)      NOT NULL,
    text_hash        VARCHAR(64),
    extracted_title  VARCHAR(500),
    extracted_price  NUMERIC(14, 2),
    currency         VARCHAR(3),
    price_source     VARCHAR(32),
    availability     VARCHAR(32)   NOT NULL DEFAULT 'UNKNOWN',
    normalized_text  TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_snapshot_source_created ON snapshot (source_id, created_at DESC);

CREATE TABLE watch (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    source_id               BIGINT        NOT NULL REFERENCES monitoring_source (id) ON DELETE RESTRICT,
    title                   VARCHAR(200)  NOT NULL,
    watch_type              VARCHAR(32)   NOT NULL,
    target_price            NUMERIC(14, 2),
    target_keyword          VARCHAR(200),
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    alert_mode              VARCHAR(16)   NOT NULL DEFAULT 'REPEAT',
    cooldown_minutes        INTEGER       NOT NULL DEFAULT 60,
    check_interval_minutes  INTEGER       NOT NULL DEFAULT 60,
    last_observed_value     VARCHAR(500),
    last_evaluated_at       TIMESTAMPTZ,
    last_alert_fingerprint  VARCHAR(64),
    expires_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_triggered_at       TIMESTAMPTZ,
    CONSTRAINT ck_watch_price CHECK (target_price IS NULL OR target_price > 0),
    CONSTRAINT ck_watch_cooldown CHECK (cooldown_minutes >= 0),
    CONSTRAINT ck_watch_interval CHECK (check_interval_minutes > 0)
);
CREATE INDEX ix_watch_user ON watch (user_id, created_at DESC);
CREATE INDEX ix_watch_source_active ON watch (source_id) WHERE active;

CREATE TABLE alert (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    watch_id      BIGINT        NOT NULL REFERENCES watch (id) ON DELETE CASCADE,
    alert_type    VARCHAR(32)   NOT NULL,
    title         VARCHAR(250)  NOT NULL,
    message       VARCHAR(1000) NOT NULL,
    old_value     VARCHAR(500),
    new_value     VARCHAR(500),
    fingerprint   VARCHAR(64)      NOT NULL,
    is_read       BOOLEAN       NOT NULL DEFAULT FALSE,
    push_status   VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_alert_user_created ON alert (user_id, created_at DESC);
CREATE INDEX ix_alert_user_unread ON alert (user_id) WHERE NOT is_read;
CREATE INDEX ix_alert_watch_created ON alert (watch_id, created_at DESC);

CREATE TABLE push_subscription (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    endpoint      VARCHAR(2048) NOT NULL,
    public_key    VARCHAR(255)  NOT NULL,
    auth_key      VARCHAR(255)  NOT NULL,
    user_agent    VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    failure_count INTEGER       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_push_endpoint ON push_subscription (endpoint);
CREATE INDEX ix_push_user ON push_subscription (user_id);

-- Monitoring history: one row per check attempt.
CREATE TABLE check_run (
    id            BIGSERIAL PRIMARY KEY,
    source_id     BIGINT        NOT NULL REFERENCES monitoring_source (id) ON DELETE CASCADE,
    started_at    TIMESTAMPTZ   NOT NULL,
    finished_at   TIMESTAMPTZ   NOT NULL,
    outcome       VARCHAR(32)   NOT NULL,
    http_status   INTEGER,
    duration_ms   BIGINT        NOT NULL DEFAULT 0,
    message       VARCHAR(500),
    snapshot_id   BIGINT        REFERENCES snapshot (id) ON DELETE SET NULL,
    alerts_created INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_check_run_source_started ON check_run (source_id, started_at DESC);
CREATE INDEX ix_check_run_started ON check_run (started_at);

-- State for deterministic development demo sources (demo:// URLs). Never used when demo mode is off.
CREATE TABLE demo_page (
    slug          VARCHAR(64)   PRIMARY KEY,
    title         VARCHAR(200)  NOT NULL,
    price         NUMERIC(14, 2),
    currency      VARCHAR(3),
    availability  VARCHAR(32)   NOT NULL,
    body_text     TEXT          NOT NULL,
    fail_next     INTEGER       NOT NULL DEFAULT 0,
    revision      INTEGER       NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
