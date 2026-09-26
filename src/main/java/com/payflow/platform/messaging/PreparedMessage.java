package com.payflow.platform.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.nio.charset.StandardCharsets;

/** A fully built message: envelope, wire JSON and routing, ready to be stored in an outbox or sent. */
public record PreparedMessage(String topic, String key, EventEnvelope envelope, String json, String traceparent) {

    public ProducerRecord<String, String> toRecord() {
        return toRecord(topic, key, json, envelope.eventId().toString(), envelope.eventType(),
                envelope.eventVersion(), envelope.producer(), envelope.correlationId(), traceparent);
    }

    public static ProducerRecord<String, String> toRecord(String topic, String key, String json, String eventId,
                                                          String eventType, int eventVersion, String producer,
                                                          String correlationId, String traceparent) {
        RecordHeaders headers = new RecordHeaders();
        add(headers, MessagingHeaders.EVENT_ID, eventId);
        add(headers, MessagingHeaders.EVENT_TYPE, eventType);
        add(headers, MessagingHeaders.EVENT_VERSION, Integer.toString(eventVersion));
        add(headers, MessagingHeaders.PRODUCER, producer);
        add(headers, MessagingHeaders.CORRELATION_ID, correlationId);
        add(headers, MessagingHeaders.TRACEPARENT, traceparent);
        return new ProducerRecord<>(topic, null, key, json, headers);
    }

    private static void add(RecordHeaders headers, String name, String value) {
        if (value != null) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
