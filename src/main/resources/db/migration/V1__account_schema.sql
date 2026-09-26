-- Account bounded context. Owned exclusively by com.payflow.account.
-- No other context may read or join these tables; they use LookupAccountUseCase instead.
CREATE SCHEMA IF NOT EXISTS account;

CREATE TABLE account.account (
    id            UUID         PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    currency      VARCHAR(3)   NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_account_currency_iso CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN'))
);

-- "My accounts" lookups and ownership checks.
CREATE INDEX ix_account_owner_subject ON account.account (owner_subject);
