-- WP-03 (K3): operable manual review.

-- The step whose outcome was unknown when the saga was escalated; an operator resolves the case by resuming it.
ALTER TABLE payment.payment_saga
    ADD COLUMN escalated_from VARCHAR(30),
    ADD CONSTRAINT ck_payment_saga_escalated_from
        CHECK (escalated_from IS NULL OR escalated_from IN ('AWAITING_SETTLEMENT', 'AWAITING_CAPTURE', 'COMPENSATING')),
    ADD CONSTRAINT ck_payment_saga_review_has_origin CHECK (step <> 'MANUAL_REVIEW' OR escalated_from IS NOT NULL);

-- Review queue (list oldest first) and the monitoring query; MANUAL_REVIEW is outside ix_payment_saga_in_flight.
CREATE INDEX ix_payment_saga_manual_review ON payment.payment_saga (step_started_at) WHERE step = 'MANUAL_REVIEW';

-- Append-only audit of operator decisions. The runtime role gets INSERT and SELECT only (R__runtime_role_grants):
-- a decision can never be edited or deleted by the application.
CREATE TABLE payment.manual_review_decision (
    id               UUID         PRIMARY KEY,
    idempotency_key  VARCHAR(100) NOT NULL,
    payment_id       UUID         NOT NULL,
    saga_id          UUID         NOT NULL,
    decision         VARCHAR(30)  NOT NULL,
    escalated_from   VARCHAR(30)  NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    ticket_reference VARCHAR(100),
    operator_subject VARCHAR(255) NOT NULL,
    evidence         JSONB        NOT NULL,
    correlation_id   VARCHAR(64)  NOT NULL,
    decided_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_manual_review_decision_key UNIQUE (idempotency_key),
    CONSTRAINT ck_manual_review_decision CHECK (decision IN ('RESUME', 'CONFIRM_NOT_SETTLED'))
);
CREATE INDEX ix_manual_review_decision_payment ON payment.manual_review_decision (payment_id, decided_at);
