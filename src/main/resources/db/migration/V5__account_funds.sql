-- WP-02 funds control (fixes WP-01 finding M2). Owned by the Account context.
--
-- available + reserved = the customer's money held by PayFlow. A payment moves `amount` from available to
-- reserved (hold), then either captures it (reserved -> payee.available) or releases it (reserved -> available).
-- The CHECK constraints are the last line of defence against overdraft, whatever code path runs.
CREATE TABLE account.account_balance (
    account_id UUID          PRIMARY KEY,
    currency   VARCHAR(3)    NOT NULL,
    available  NUMERIC(19,4) NOT NULL,
    reserved   NUMERIC(19,4) NOT NULL,
    version    BIGINT        NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_balance_account                 FOREIGN KEY (account_id) REFERENCES account.account (id),
    CONSTRAINT ck_balance_available_non_negative  CHECK (available >= 0),
    CONSTRAINT ck_balance_reserved_non_negative   CHECK (reserved >= 0),
    CONSTRAINT ck_balance_currency_iso            CHECK (currency ~ '^[A-Z]{3}$')
);

-- Accounts opened before WP-02 start with a zero balance.
INSERT INTO account.account_balance (account_id, currency, available, reserved, version, updated_at)
SELECT id, currency, 0, 0, 0, now() FROM account.account;

-- One reservation per payment: the natural idempotency key for ReserveFunds / CaptureFunds / ReleaseFunds.
CREATE TABLE account.funds_reservation (
    id               UUID          PRIMARY KEY,
    payment_id       UUID          NOT NULL,
    payer_account_id UUID          NOT NULL,
    payee_account_id UUID,
    amount           NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    reason           VARCHAR(255),
    version          BIGINT        NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_funds_reservation_payment  UNIQUE (payment_id),
    CONSTRAINT ck_funds_reservation_amount   CHECK (amount > 0),
    CONSTRAINT ck_funds_reservation_status   CHECK (status IN ('RESERVED', 'CAPTURED', 'RELEASED', 'REJECTED'))
);

-- Deposits (treasury top-ups). The client-supplied id makes deposits idempotent.
CREATE TABLE account.funds_deposit (
    id         UUID          PRIMARY KEY,
    account_id UUID          NOT NULL,
    amount     NUMERIC(19,4) NOT NULL,
    currency   VARCHAR(3)    NOT NULL,
    created_by VARCHAR(255)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_deposit_account   FOREIGN KEY (account_id) REFERENCES account.account (id),
    CONSTRAINT ck_deposit_amount    CHECK (amount > 0)
);
