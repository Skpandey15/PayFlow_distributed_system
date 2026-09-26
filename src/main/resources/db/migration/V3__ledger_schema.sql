-- Ledger bounded context: append-only double-entry journal. Owned exclusively by com.payflow.ledger.
CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.journal_entry (
    id          UUID         PRIMARY KEY,
    reference   VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    currency    VARCHAR(3)   NOT NULL,
    posted_at   TIMESTAMPTZ  NOT NULL,
    -- Idempotent posting: one journal entry per business event (e.g. payment:<id>:settlement).
    CONSTRAINT uq_journal_entry_reference UNIQUE (reference),
    CONSTRAINT ck_journal_currency_iso    CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE ledger.ledger_entry (
    id               UUID          PRIMARY KEY,
    journal_entry_id UUID          NOT NULL,
    line_no          SMALLINT      NOT NULL,
    account_id       UUID          NOT NULL,
    direction        VARCHAR(6)    NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    CONSTRAINT fk_ledger_entry_journal  FOREIGN KEY (journal_entry_id) REFERENCES ledger.journal_entry (id),
    CONSTRAINT uq_ledger_entry_line     UNIQUE (journal_entry_id, line_no),
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_amount   CHECK (amount > 0)
);

-- Balance derivation: SUM over one account's lines in one currency.
CREATE INDEX ix_ledger_entry_account_currency ON ledger.ledger_entry (account_id, currency);

-- Invariant 1: every journal entry balances (sum debits = sum credits) and uses one currency.
-- DEFERRABLE INITIALLY DEFERRED so it is checked at COMMIT, after all lines of the entry are inserted.
CREATE FUNCTION ledger.assert_journal_balanced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    imbalance  NUMERIC;
    currencies INTEGER;
BEGIN
    SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0),
           COUNT(DISTINCT currency)
      INTO imbalance, currencies
      FROM ledger.ledger_entry
     WHERE journal_entry_id = NEW.journal_entry_id;
    IF imbalance <> 0 THEN
        RAISE EXCEPTION 'journal entry % is unbalanced by %', NEW.journal_entry_id, imbalance
            USING ERRCODE = 'check_violation';
    END IF;
    IF currencies > 1 THEN
        RAISE EXCEPTION 'journal entry % mixes currencies', NEW.journal_entry_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_entry_balanced
    AFTER INSERT ON ledger.ledger_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger.assert_journal_balanced();

-- Invariant 2: the ledger is append-only. Corrections are new compensating entries, never edits.
CREATE FUNCTION ledger.reject_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'ledger is append-only: % on %.% is not allowed', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_journal_entry_append_only
    BEFORE UPDATE OR DELETE ON ledger.journal_entry
    FOR EACH ROW EXECUTE FUNCTION ledger.reject_mutation();

CREATE TRIGGER trg_ledger_entry_append_only
    BEFORE UPDATE OR DELETE ON ledger.ledger_entry
    FOR EACH ROW EXECUTE FUNCTION ledger.reject_mutation();

CREATE TRIGGER trg_journal_entry_no_truncate
    BEFORE TRUNCATE ON ledger.journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION ledger.reject_mutation();

CREATE TRIGGER trg_ledger_entry_no_truncate
    BEFORE TRUNCATE ON ledger.ledger_entry
    FOR EACH STATEMENT EXECUTE FUNCTION ledger.reject_mutation();
