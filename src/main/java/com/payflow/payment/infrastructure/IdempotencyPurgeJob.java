package com.payflow.payment.infrastructure;

import com.payflow.payment.application.port.in.PurgeExpiredIdempotencyKeysUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically enforces the idempotency retention policy. Every replica runs it. The DELETE is
 * idempotent and index-driven, so concurrent runs are safe. A distributed lock (e.g. ShedLock) is only
 * worth adding if the purge ever becomes expensive (WP-03 decision).
 */
@Component
class IdempotencyPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyPurgeJob.class);

    private final PurgeExpiredIdempotencyKeysUseCase purge;

    IdempotencyPurgeJob(PurgeExpiredIdempotencyKeysUseCase purge) {
        this.purge = purge;
    }

    @Scheduled(fixedDelayString = "${payflow.payment.idempotency-purge-interval:PT1H}",
            initialDelayString = "${payflow.payment.idempotency-purge-interval:PT1H}")
    void purgeExpired() {
        int removed = purge.purgeExpired();
        log.atInfo().addKeyValue("removed", removed).log("purged expired idempotency records");
    }
}
