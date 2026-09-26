package com.payflow.shared.application;

/**
 * Optimistic-concurrency check failed: another transaction modified the aggregate after it was read.
 * Nothing was written; the caller should re-read and retry.
 */
public class ConcurrencyConflictException extends ConflictException {

    public ConcurrencyConflictException(String message, Throwable cause) {
        super("CONCURRENT_MODIFICATION", message, cause);
    }
}
