package com.payflow.platform.messaging.error;

/**
 * Classification of asynchronous processing failures. The category, not the exception class, decides the
 * handling policy (see docs/architecture/EXCEPTION-ARCHITECTURE.md).
 */
public enum FailureCategory {

    /** Payload is not parseable JSON or not an envelope. A poison message. */
    DESERIALIZATION(false),
    /** Parseable, but violates the contract (missing mandatory field, key/aggregate mismatch, unknown type). */
    CONTRACT_VIOLATION(false),
    /** A version this consumer cannot read (too new, or retired). */
    UNSUPPORTED_VERSION(false),
    /** Wrong producer for the topic, or wrong topic for the type. Treated as a security signal. */
    UNTRUSTED_SOURCE(false),
    /** A business rule rejects the message; retrying cannot change the outcome. */
    BUSINESS_RULE(false),
    /** A database constraint rejected the change; retrying the same data will fail the same way. */
    DATA_INTEGRITY(false),
    /** Optimistic/pessimistic lock conflict or deadlock: re-reading fresh state usually succeeds. */
    CONCURRENCY(true),
    /** Database, MongoDB, Kafka or a rail temporarily unreachable. */
    TRANSIENT_INFRASTRUCTURE(true),
    /** Unclassified (likely a bug). Retried a bounded number of times, then dead-lettered for investigation. */
    UNKNOWN(true);

    private final boolean retryable;

    FailureCategory(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
