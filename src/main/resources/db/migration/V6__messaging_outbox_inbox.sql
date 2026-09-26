-- WP-02 Transactional Outbox (producers) and Inbox / processed-event table (idempotent consumers).
-- Each context owns its own tables in its own schema: no shared messaging tables across contexts.
--
-- outbox_event.id (BIGSERIAL) is the publication order. Rows for one aggregate are always inserted after that
-- aggregate's versioned UPDATE in the same transaction, so per-aggregate id order equals commit order.

-- Outboxes: contexts that emit events from PostgreSQL state changes.
CREATE TABLE payment.outbox_event (
    id               BIGSERIAL    PRIMARY KEY,
    event_id         UUID         NOT NULL,
    aggregate_type   VARCHAR(50)  NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,
    topic            VARCHAR(100) NOT NULL,
    message_key      VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    event_version    INT          NOT NULL,
    envelope         JSONB        NOT NULL,
    correlation_id   VARCHAR(64)  NOT NULL,
    traceparent      VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL,
    published_at     TIMESTAMPTZ,
    publish_attempts INT          NOT NULL DEFAULT 0,
    last_error       VARCHAR(255),
    CONSTRAINT uq_payment_outbox_event_id UNIQUE (event_id)
);
-- Relay scan: only unpublished rows, in publication order.
CREATE INDEX ix_payment_outbox_unpublished ON payment.outbox_event (id) WHERE published_at IS NULL;

CREATE TABLE account.outbox_event (
    id               BIGSERIAL    PRIMARY KEY,
    event_id         UUID         NOT NULL,
    aggregate_type   VARCHAR(50)  NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,
    topic            VARCHAR(100) NOT NULL,
    message_key      VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    event_version    INT          NOT NULL,
    envelope         JSONB        NOT NULL,
    correlation_id   VARCHAR(64)  NOT NULL,
    traceparent      VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL,
    published_at     TIMESTAMPTZ,
    publish_attempts INT          NOT NULL DEFAULT 0,
    last_error       VARCHAR(255),
    CONSTRAINT uq_account_outbox_event_id UNIQUE (event_id)
);
-- Relay scan: only unpublished rows, in publication order.
CREATE INDEX ix_account_outbox_unpublished ON account.outbox_event (id) WHERE published_at IS NULL;

CREATE TABLE settlement.outbox_event (
    id               BIGSERIAL    PRIMARY KEY,
    event_id         UUID         NOT NULL,
    aggregate_type   VARCHAR(50)  NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,
    topic            VARCHAR(100) NOT NULL,
    message_key      VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    event_version    INT          NOT NULL,
    envelope         JSONB        NOT NULL,
    correlation_id   VARCHAR(64)  NOT NULL,
    traceparent      VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL,
    published_at     TIMESTAMPTZ,
    publish_attempts INT          NOT NULL DEFAULT 0,
    last_error       VARCHAR(255),
    CONSTRAINT uq_settlement_outbox_event_id UNIQUE (event_id)
);
-- Relay scan: only unpublished rows, in publication order.
CREATE INDEX ix_settlement_outbox_unpublished ON settlement.outbox_event (id) WHERE published_at IS NULL;

-- Inboxes: consumers whose effect is a single local transaction. The PK is the duplicate arbiter.
CREATE TABLE payment.processed_event (
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID         NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_payment_processed_event PRIMARY KEY (consumer, event_id)
);
CREATE INDEX ix_payment_processed_event_at ON payment.processed_event (processed_at);

CREATE TABLE account.processed_event (
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID         NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_account_processed_event PRIMARY KEY (consumer, event_id)
);
CREATE INDEX ix_account_processed_event_at ON account.processed_event (processed_at);

CREATE TABLE ledger.processed_event (
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID         NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_ledger_processed_event PRIMARY KEY (consumer, event_id)
);
CREATE INDEX ix_ledger_processed_event_at ON ledger.processed_event (processed_at);
