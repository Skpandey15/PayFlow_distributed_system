-- Least privilege (Zero Trust): Flyway migrates as the schema OWNER, while the application connects as a
-- separate runtime role that can only do DML, and only the DML each table needs. A compromised application
-- therefore cannot DROP/ALTER tables or rewrite the ledger. The UPDATE/DELETE privileges are absent on
-- ledger.*, on top of the append-only triggers.
--
-- The block is a no-op when the runtime role does not exist (e.g. Testcontainers, where a single
-- user runs everything), so the same migrations work in every environment.
DO
$$
DECLARE
    runtime_role TEXT := '${runtime_role}';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE NOTICE 'Runtime role % not present; skipping grants', runtime_role;
        RETURN;
    END IF;

    EXECUTE format('GRANT USAGE ON SCHEMA account, payment, ledger, settlement TO %I', runtime_role);

    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA account TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON payment.payment TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, DELETE ON payment.idempotency_record TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA settlement TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA ledger TO %I', runtime_role);
END;
$$;
