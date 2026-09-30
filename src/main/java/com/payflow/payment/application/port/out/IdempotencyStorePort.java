package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.PaymentId;

import java.time.Instant;
import java.util.Optional;

/**
 * Durable record of which idempotency key produced which payment. Keys are scoped per client (JWT
 * subject), so two clients can never collide on, or probe, each other's keys.
 */
public interface IdempotencyStorePort {

    Optional<IdempotencyRecord> find(String clientId, String idempotencyKey);

    /**
     * Must be called in the same transaction that inserts the payment.
     *
     * @throws IdempotencyKeyConflictException if the key was committed by a concurrent request
     */
    void add(IdempotencyRecord record);

    int deleteExpired(Instant now);

    record IdempotencyRecord(String clientId, String idempotencyKey, String requestFingerprint,
                             PaymentId paymentId, Instant createdAt, Instant expiresAt) {
    }

    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String idempotencyKey, Throwable cause) {
            super("Idempotency key already recorded: " + idempotencyKey, cause);
        }
    }
}
