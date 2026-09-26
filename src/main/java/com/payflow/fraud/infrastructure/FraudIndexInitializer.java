package com.payflow.fraud.infrastructure;

import com.payflow.fraud.adapter.out.persistence.MongoFraudAssessmentAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures MongoDB indexes after startup. This must not block startup: if MongoDB is down, Payment, Account
 * and Ledger keep serving and only authorization is degraded (fail closed). Indexes are then created on the
 * next start. The unique index backs the one-assessment-per-payment guarantee, so a failure is logged at ERROR.
 */
@Component
class FraudIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(FraudIndexInitializer.class);

    private final MongoFraudAssessmentAdapter adapter;

    FraudIndexInitializer(MongoFraudAssessmentAdapter adapter) {
        this.adapter = adapter;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ensureIndexes() {
        try {
            adapter.ensureIndexes();
        } catch (RuntimeException e) {
            log.error("Could not ensure MongoDB fraud indexes; fraud idempotency relies on them", e);
        }
    }
}
