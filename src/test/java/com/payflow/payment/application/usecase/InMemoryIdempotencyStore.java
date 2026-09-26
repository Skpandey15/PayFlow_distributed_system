package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.out.IdempotencyStorePort;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test fake that can simulate a concurrent request committing the same key first (the race window). */
class InMemoryIdempotencyStore implements IdempotencyStorePort {

    final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
    private IdempotencyRecord concurrentWinner;

    /** On the next {@link #add}, behave as if another transaction had just committed {@code winner}. */
    void simulateConcurrentWinner(IdempotencyRecord winner) {
        this.concurrentWinner = winner;
    }

    @Override
    public Optional<IdempotencyRecord> find(String clientId, String idempotencyKey) {
        return Optional.ofNullable(records.get(clientId + "|" + idempotencyKey));
    }

    @Override
    public void add(IdempotencyRecord record) {
        if (concurrentWinner != null) {
            records.put(record.clientId() + "|" + record.idempotencyKey(), concurrentWinner);
            concurrentWinner = null;
            throw new IdempotencyKeyConflictException(record.idempotencyKey(), null);
        }
        if (records.putIfAbsent(record.clientId() + "|" + record.idempotencyKey(), record) != null) {
            throw new IdempotencyKeyConflictException(record.idempotencyKey(), null);
        }
    }

    @Override
    public int deleteExpired(Instant now) {
        int before = records.size();
        records.values().removeIf(r -> r.expiresAt().isBefore(now));
        return before - records.size();
    }
}
