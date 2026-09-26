-- Settlement bounded context. Owned exclusively by com.payflow.settlement.
CREATE SCHEMA IF NOT EXISTS settlement;

CREATE TABLE settlement.settlement (
    id                 UUID          PRIMARY KEY,
    payment_id         UUID          NOT NULL,
    rail               VARCHAR(20)   NOT NULL,
    amount             NUMERIC(19,4) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    payment_reference  VARCHAR(140),
    status             VARCHAR(20)   NOT NULL,
    provider_reference VARCHAR(100),
    decline_reason     VARCHAR(255),
    version            BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    -- One settlement per payment: resubmission resumes instead of creating a second instruction.
    CONSTRAINT uq_settlement_payment          UNIQUE (payment_id),
    CONSTRAINT ck_settlement_amount_positive  CHECK (amount > 0),
    CONSTRAINT ck_settlement_currency_iso     CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_settlement_rail             CHECK (rail IN ('CARD_NETWORK', 'UPI', 'BANK_TRANSFER')),
    CONSTRAINT ck_settlement_status           CHECK (status IN ('PENDING', 'COMPLETED', 'DECLINED')),
    CONSTRAINT ck_settlement_completed_has_ref CHECK (status <> 'COMPLETED' OR provider_reference IS NOT NULL)
);

-- Reconciliation / stuck-instruction sweeps (WP-03) scan pending settlements by age.
CREATE INDEX ix_settlement_status_updated ON settlement.settlement (status, updated_at);
