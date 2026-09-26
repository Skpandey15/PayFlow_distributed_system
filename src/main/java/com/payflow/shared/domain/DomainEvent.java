package com.payflow.shared.domain;

import java.time.Instant;

/**
 * Something that happened in the domain that other parts of the system may care about.
 * Aggregates record events; the application layer collects them after a state change and hands them to
 * an event-publisher port inside the same transaction. WP-02 implements that port as a Transactional
 * Outbox so that state change and event are committed atomically.
 */
public interface DomainEvent {

    Instant occurredAt();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
