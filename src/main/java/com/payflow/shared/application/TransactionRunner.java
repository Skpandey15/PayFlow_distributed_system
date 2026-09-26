package com.payflow.shared.application;

import java.util.function.Supplier;

/**
 * Outbound port that makes transaction boundaries explicit in use-case code.
 *
 * <p>Application services wrap exactly the work that must be atomic in {@link #inTransaction} and keep
 * remote or cross-context calls outside it. This keeps the application layer free of Spring's
 * {@code @Transactional}, whose proxy-based semantics hide where a transaction starts and silently do
 * nothing on self-invocation. It also lets unit tests substitute a trivial runner.
 */
public interface TransactionRunner {

    /** Runs {@code work} in a read-write transaction; commits on normal return, rolls back on exception. */
    <T> T inTransaction(Supplier<T> work);

    /** Runs {@code work} in a read-only transaction (consistent reads, no flush). */
    <T> T readOnly(Supplier<T> work);
}
