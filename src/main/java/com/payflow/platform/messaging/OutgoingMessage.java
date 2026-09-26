package com.payflow.platform.messaging;

import java.util.Objects;
import java.util.UUID;

/**
 * A message a context wants to emit. Topic, type and version are derived from the payload's contract class
 * ({@link com.payflow.contracts.EventCatalog}), so an adapter cannot put a payload on the wrong topic.
 *
 * @param key    Kafka record key (partitioning): paymentId for workflow messages, accountId for account messages
 * @param sagaId workflow instance, or null to inherit the one currently being processed
 */
public record OutgoingMessage(Object payload, String key, String aggregateType, String aggregateId, UUID sagaId) {

    public OutgoingMessage {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
    }

    public static OutgoingMessage of(Object payload, String aggregateType, String aggregateId) {
        return new OutgoingMessage(payload, aggregateId, aggregateType, aggregateId, null);
    }

    public static OutgoingMessage forSaga(Object payload, String paymentId, UUID sagaId) {
        return new OutgoingMessage(payload, paymentId, "Payment", paymentId, sagaId);
    }
}
