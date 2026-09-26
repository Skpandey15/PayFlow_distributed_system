package com.payflow.platform.messaging.consumer;

import com.payflow.contracts.EventCatalog.EventDefinition;
import com.payflow.platform.messaging.EventEnvelope;
import com.payflow.platform.messaging.error.InvalidEventException;

import java.util.UUID;

/**
 * A validated, decoded event handed to a context's inbound adapter. {@link #payload()} is already upcast to
 * the <em>current</em> contract version, so handlers only ever see one shape per type.
 */
public record IncomingEvent(EventEnvelope envelope, EventDefinition definition, Object payload, String topic,
                            int partition, long offset, String consumer, int deliveryAttempt) {

    public UUID eventId() {
        return envelope.eventId();
    }

    public String eventType() {
        return envelope.eventType();
    }

    public <T> T payload(Class<T> type) {
        if (!type.isInstance(payload)) {
            throw new InvalidEventException("UNEXPECTED_PAYLOAD_TYPE",
                    envelope.eventType() + " is not a " + type.getSimpleName(), null);
        }
        return type.cast(payload);
    }
}
