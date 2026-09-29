-- WP-03 (K4): continuous reconciliation. Its own schema: it records findings about other contexts' data and never
-- writes to them. Read access to the compared tables is the runtime role's existing SELECT.
CREATE SCHEMA IF NOT EXISTS reconciliation;

CREATE TABLE reconciliation.run (
    id              UUID        PRIMARY KEY,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ NOT NULL,
    records_checked BIGINT      NOT NULL,
    findings        INT         NOT NULL
);
CREATE INDEX ix_reconciliation_run_started ON reconciliation.run (started_at DESC);

-- One row per (check, subject). A repeated observation increments times_seen; "confirmed" drift = seen in >= 2 runs,
-- which filters out anything that was merely in flight at one snapshot.
CREATE TABLE reconciliation.mismatch (
    id             UUID          PRIMARY KEY,
    check_name     VARCHAR(60)   NOT NULL,
    severity       VARCHAR(10)   NOT NULL,
    subject_type   VARCHAR(20)   NOT NULL,
    subject_id     VARCHAR(64)   NOT NULL,
    currency       VARCHAR(3),
    expected       NUMERIC(19,4),
    actual         NUMERIC(19,4),
    times_seen     INT           NOT NULL,
    first_seen_at  TIMESTAMPTZ   NOT NULL,
    last_seen_at   TIMESTAMPTZ   NOT NULL,
    last_run_id    UUID          NOT NULL,
    status         VARCHAR(10)   NOT NULL,
    resolved_at    TIMESTAMPTZ,
    CONSTRAINT ck_mismatch_status   CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_mismatch_severity CHECK (severity IN ('CRITICAL', 'HIGH'))
);
-- At most one OPEN mismatch per (check, subject); history of resolved ones is kept.
CREATE UNIQUE INDEX uq_mismatch_open ON reconciliation.mismatch (check_name, subject_id) WHERE status = 'OPEN';
CREATE INDEX ix_mismatch_open_first_seen ON reconciliation.mismatch (first_seen_at) WHERE status = 'OPEN';
