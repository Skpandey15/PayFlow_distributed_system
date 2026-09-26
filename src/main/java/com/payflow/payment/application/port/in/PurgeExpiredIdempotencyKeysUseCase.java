package com.payflow.payment.application.port.in;

public interface PurgeExpiredIdempotencyKeysUseCase {

    /** Deletes idempotency records past their retention window; returns the number removed. */
    int purgeExpired();
}
