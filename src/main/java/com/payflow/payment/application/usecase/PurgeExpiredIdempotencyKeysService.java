package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.PurgeExpiredIdempotencyKeysUseCase;
import com.payflow.payment.application.port.out.IdempotencyStorePort;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;

/**
 * Retention: a key stays bound to its payment until it is purged, which is at least the retention window.
 * The purge is idempotent, so running it on several replicas at once is harmless (only wasteful).
 */
public class PurgeExpiredIdempotencyKeysService implements PurgeExpiredIdempotencyKeysUseCase {

    private final IdempotencyStorePort idempotency;
    private final TransactionRunner tx;
    private final Clock clock;

    public PurgeExpiredIdempotencyKeysService(IdempotencyStorePort idempotency, TransactionRunner tx, Clock clock) {
        this.idempotency = idempotency;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public int purgeExpired() {
        return tx.inTransaction(() -> idempotency.deleteExpired(clock.instant()));
    }
}
