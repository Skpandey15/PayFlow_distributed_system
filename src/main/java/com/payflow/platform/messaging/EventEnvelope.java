package com.payflow.platform.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard envelope of every message on the backbone (the Kafka record value, JSON).
 *
 * @param eventId       unique per emitted message; the idempotency key for consumers (inbox)
 * @param eventType     contract name, e.g. {@code FundsReserved} (see {@link com.payflow.contracts.EventCatalog})
 * @param eventVersion  contract version of {@code payload}
 * @param producer      logical producing service; validated against the topic owner by consumers
 * @param aggregateType aggregate the message is about (Payment, Account, ...)
 * @param aggregateId   aggregate identity; equals the Kafka record key
 * @param occurredAt    when the fact happened / command was issued
 * @param correlationId originating business request
 * @param causationId   the event that caused this one (null for API-initiated events)
 * @param sagaId        saga instance for workflow messages (null otherwise)
 * @param payload       the versioned contract payload
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String producer,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        UUID causationId,
        UUID sagaId,
        JsonNode payload) {
}
