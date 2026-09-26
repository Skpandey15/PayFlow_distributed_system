-- Payment bounded context. Owned exclusively by com.payflow.payment.
-- payer/payee account ids are references by identity only: there is deliberately NO foreign key into
-- account.account, because that would couple two contexts' schemas and block extracting them into
-- separate services/databases. Referential validity is checked through the Account context's API.
CREATE SCHEMA IF NOT EXISTS payment;

CREATE TABLE payment.payment (
    id               UUID          PRIMARY KEY,
    payer_account_id UUID          NOT NULL,
    payee_account_id UUID          NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    method           VARCHAR(20)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    reference        VARCHAR(140),
    initiated_by     VARCHAR(255)  NOT NULL,
    failure_reason   VARCHAR(255),
    version          BIGINT        NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    -- Defence in depth: the domain enforces these too, but the database is the last line of defence
    -- against any code path (migration script, manual fix, future service) that bypasses the aggregate.
    CONSTRAINT ck_payment_amount_positive   CHECK (amount > 0),
    CONSTRAINT ck_payment_currency_iso      CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_distinct_parties  CHECK (payer_account_id <> payee_account_id),
    CONSTRAINT ck_payment_method            CHECK (method IN ('CARD', 'UPI', 'BANK_TRANSFER')),
    CONSTRAINT ck_payment_status            CHECK (status IN ('CREATED', 'AUTHORIZED', 'REJECTED', 'CANCELLED',
                                                              'PROCESSING', 'SETTLED', 'FAILED')),
    CONSTRAINT ck_payment_failure_reason    CHECK ((status IN ('REJECTED', 'FAILED')) = (failure_reason IS NOT NULL))
);

-- "My payments, newest first" (the dominant customer query); id breaks ties for stable pagination.
CREATE INDEX ix_payment_initiator_created ON payment.payment (initiated_by, created_at DESC, id);
-- Operations view filtered by status.
CREATE INDEX ix_payment_status_created ON payment.payment (status, created_at DESC);

-- API idempotency. The PRIMARY KEY is the concurrency control: concurrent requests with the same
-- (client, key) serialise on this index, and exactly one INSERT can commit.
CREATE TABLE payment.idempotency_record (
    client_id           VARCHAR(255) NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    payment_id          UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (client_id, idempotency_key),
    CONSTRAINT fk_idempotency_payment FOREIGN KEY (payment_id) REFERENCES payment.payment (id),
    CONSTRAINT ck_idempotency_expiry  CHECK (expires_at > created_at)
);

-- Retention purge scans by expiry.
CREATE INDEX ix_idempotency_expires_at ON payment.idempotency_record (expires_at);
