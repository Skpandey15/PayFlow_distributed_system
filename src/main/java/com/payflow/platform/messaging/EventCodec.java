package com.payflow.platform.messaging;

import com.payflow.contracts.EventCatalog.EventDefinition;
import com.payflow.platform.messaging.error.EventDeserializationException;
import com.payflow.platform.messaging.error.InvalidEventException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * JSON encoding of {@link EventEnvelope}. Decoding is strict about the envelope, which is our own contract and
 * must be complete, and tolerant about unknown payload fields (tolerant reader, which is what makes additive
 * schema changes forward compatible).
 */
@Component
public class EventCodec {

    private final JsonMapper mapper;

    public EventCodec(JsonMapper jsonMapper) {
        this.mapper = jsonMapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public JsonNode toTree(Object payload) {
        return mapper.valueToTree(payload);
    }

    public String encode(EventEnvelope e) {
        ObjectNode node = mapper.createObjectNode();
        node.put("eventId", e.eventId().toString());
        node.put("eventType", e.eventType());
        node.put("eventVersion", e.eventVersion());
        node.put("producer", e.producer());
        node.put("aggregateType", e.aggregateType());
        node.put("aggregateId", e.aggregateId());
        node.put("occurredAt", e.occurredAt().toString());
        node.put("correlationId", e.correlationId());
        node.put("causationId", e.causationId() == null ? null : e.causationId().toString());
        node.put("sagaId", e.sagaId() == null ? null : e.sagaId().toString());
        node.set("payload", e.payload());
        return mapper.writeValueAsString(node);
    }

    public EventEnvelope decode(String json) {
        if (json == null || json.isBlank()) {
            throw new EventDeserializationException("Empty record value", null);
        }
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new EventDeserializationException("Record value is not JSON", e);
        }
        if (node == null || !node.isObject()) {
            throw new EventDeserializationException("Record value is not a JSON object", null);
        }
        try {
            JsonNode payload = node.get("payload");
            if (payload == null || !payload.isObject()) {
                throw new InvalidEventException("ENVELOPE_PAYLOAD_MISSING", "Envelope has no payload object", null);
            }
            int version = node.path("eventVersion").asInt(-1);
            if (version < 1) {
                throw new InvalidEventException("ENVELOPE_VERSION_INVALID", "eventVersion must be >= 1", null);
            }
            return new EventEnvelope(
                    UUID.fromString(required(node, "eventId")),
                    required(node, "eventType"),
                    version,
                    required(node, "producer"),
                    required(node, "aggregateType"),
                    required(node, "aggregateId"),
                    Instant.parse(required(node, "occurredAt")),
                    required(node, "correlationId"),
                    optionalUuid(node, "causationId"),
                    optionalUuid(node, "sagaId"),
                    payload);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidEventException("ENVELOPE_FIELD_MALFORMED", "Malformed envelope field", e);
        }
    }

    /** Decodes the payload in its own version, then upcasts it to the current contract version. */
    public Object payload(EventEnvelope envelope, EventDefinition definition) {
        Class<?> type = definition.payloadType(envelope.eventVersion());
        try {
            Object decoded = mapper.treeToValue(envelope.payload(), type);
            return definition.upcast(decoded, envelope.eventVersion());
        } catch (JacksonException e) {
            throw new InvalidEventException("PAYLOAD_CONTRACT_VIOLATION",
                    "Payload does not satisfy " + definition.eventType() + " v" + envelope.eventVersion(), e);
        }
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            throw new InvalidEventException("ENVELOPE_FIELD_MISSING", "Envelope field missing: " + field, null);
        }
        return value.asString();
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : UUID.fromString(value.asString());
    }
}
