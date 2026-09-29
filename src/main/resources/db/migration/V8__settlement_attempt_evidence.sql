-- WP-03: evidence of unanswered rail submissions, so an UNKNOWN outcome can be resolved by an operator with facts
-- (did the instruction provably not leave, or may the rail have processed it?) instead of guesses.
ALTER TABLE settlement.settlement
    ADD COLUMN submission_attempts  INT         NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_outcome VARCHAR(20),
    ADD COLUMN last_error_code      VARCHAR(64),
    ADD COLUMN last_attempt_at      TIMESTAMPTZ,
    ADD CONSTRAINT ck_settlement_attempt_outcome
        CHECK (last_attempt_outcome IS NULL OR last_attempt_outcome IN ('ANSWERED', 'NOT_SENT', 'UNKNOWN')),
    ADD CONSTRAINT ck_settlement_attempts CHECK (submission_attempts >= 0);
