package com.payflow.platform.messaging;

import com.payflow.contracts.EventCatalog;
import com.payflow.contracts.EventCatalog.EventDefinition;
import com.payflow.shared.domain.Identifiers;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Builds envelopes for outgoing messages. Identity, topic and version come from the contract catalog, and the
 * correlation, causation and saga context comes from the unit of work in progress ({@link MessageContext}).
 *
 * <p>Producer-side authorisation: a context may only emit message types it owns. A misrouted payload fails
 * here, at development time, before it can reach Kafka.
 */
@Component
public class EnvelopeFactory {

    private final EventCodec codec;
    private final TraceparentSupplier traceparent;
    private final Clock clock;

    public EnvelopeFactory(EventCodec codec, TraceparentSupplier traceparent, Clock clock) {
        this.codec = codec;
        this.traceparent = traceparent;
        this.clock = clock;
    }

    public PreparedMessage prepare(String producer, OutgoingMessage message) {
        EventDefinition definition = EventCatalog.forPayload(message.payload().getClass());
        if (!definition.producer().equals(producer)) {
            throw new IllegalStateException(producer + " is not the owner of " + definition.eventType()
                    + " (owner: " + definition.producer() + ")");
        }
        UUID eventId = Identifiers.timeOrderedUuid();
        String correlationId = MessageContext.correlationId() != null ? MessageContext.correlationId() : eventId.toString();
        UUID sagaId = message.sagaId() != null ? message.sagaId() : MessageContext.sagaId();
        EventEnvelope envelope = new EventEnvelope(eventId, definition.eventType(), definition.currentVersion(), producer,
                message.aggregateType(), message.aggregateId(), clock.instant(), correlationId,
                MessageContext.causationId(), sagaId, codec.toTree(message.payload()));
        return new PreparedMessage(definition.topic(), message.key(), envelope, codec.encode(envelope), traceparent.current());
    }
}
