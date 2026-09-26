-- WP-02 orchestrated payment saga state (Payment context). One saga per payment.
CREATE TABLE payment.payment_saga (
    saga_id             UUID         PRIMARY KEY,
    payment_id          UUID         NOT NULL,
    step                VARCHAR(30)  NOT NULL,
    compensation_reason VARCHAR(30),
    outcome_reason      VARCHAR(255),
    step_attempts       INT          NOT NULL,
    step_started_at     TIMESTAMPTZ  NOT NULL,
    correlation_id      VARCHAR(64)  NOT NULL,
    checkout_device_id  VARCHAR(128),
    checkout_ip_address VARCHAR(45),
    checkout_user_agent VARCHAR(512),
    checkout_country    VARCHAR(2),
    version             BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_payment_saga_payment  UNIQUE (payment_id),
    CONSTRAINT fk_payment_saga_payment  FOREIGN KEY (payment_id) REFERENCES payment.payment (id),
    CONSTRAINT ck_payment_saga_step     CHECK (step IN ('AWAITING_RISK', 'AWAITING_FUNDS', 'AWAITING_SETTLEMENT',
                                                        'AWAITING_CAPTURE', 'COMPENSATING', 'COMPLETED', 'REJECTED',
                                                        'FAILED', 'CANCELLED', 'MANUAL_REVIEW')),
    CONSTRAINT ck_payment_saga_attempts CHECK (step_attempts >= 0)
);

-- Stuck-saga scanner: only in-flight sagas, oldest step first.
CREATE INDEX ix_payment_saga_in_flight ON payment.payment_saga (step_started_at)
    WHERE step IN ('AWAITING_RISK', 'AWAITING_FUNDS', 'AWAITING_SETTLEMENT', 'AWAITING_CAPTURE', 'COMPENSATING');
