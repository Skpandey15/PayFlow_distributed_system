-- Least privilege (Zero Trust): Flyway migrates as the schema OWNER, while the application connects as a
-- separate runtime role that can only do DML, and only the DML each table needs. A compromised application
-- therefore cannot DROP/ALTER tables or rewrite the ledger. The UPDATE/DELETE privileges are absent on the
-- ledger journal tables, on top of the append-only triggers.
--
-- The block is a no-op when the runtime role does not exist (e.g. Testcontainers, where a single user runs
-- everything), so the same migrations work in every environment.
--
-- Maintenance rule: edit this file (changing its checksum) whenever a migration adds a table, so Flyway re-applies it.
-- Revision: WP-03 (manual-review audit, reconciliation).
DO
$$
DECLARE
    runtime_role TEXT := '${runtime_role}';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE NOTICE 'Runtime role % not present; skipping grants', runtime_role;
        RETURN;
    END IF;

    EXECUTE format('GRANT USAGE ON SCHEMA account, payment, ledger, settlement, reconciliation TO %I', runtime_role);

    -- Reconciliation: writes only its own findings; reads the other schemas through their existing SELECT grants.
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA reconciliation TO %I', runtime_role);

    -- Account: master data, balances, reservations, deposits, outbox and inbox.
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA account TO %I', runtime_role);
    EXECUTE format('GRANT DELETE ON account.outbox_event, account.processed_event TO %I', runtime_role);

    -- Payment: payments, sagas, idempotency, outbox and inbox.
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON payment.payment, payment.payment_saga, payment.outbox_event TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, DELETE ON payment.idempotency_record, payment.processed_event TO %I', runtime_role);
    EXECUTE format('GRANT DELETE ON payment.outbox_event TO %I', runtime_role);
    -- Manual-review audit: append-only for the application (no UPDATE, no DELETE).
    EXECUTE format('GRANT SELECT, INSERT ON payment.manual_review_decision TO %I', runtime_role);

    -- Settlement.
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA settlement TO %I', runtime_role);
    EXECUTE format('GRANT DELETE ON settlement.outbox_event TO %I', runtime_role);

    -- Ledger: append-only journal (no UPDATE/DELETE), inbox with purge.
    EXECUTE format('GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA ledger TO %I', runtime_role);
    EXECUTE format('GRANT DELETE ON ledger.processed_event TO %I', runtime_role);

    -- BIGSERIAL outbox ids.
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA account, payment, settlement TO %I', runtime_role);
END;
$$;
