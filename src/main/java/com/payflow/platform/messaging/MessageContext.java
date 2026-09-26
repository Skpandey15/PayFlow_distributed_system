package com.payflow.platform.messaging;

import com.payflow.platform.observability.CorrelationIdFilter;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Identifiers of the unit of work currently executing, kept in the logging MDC so that every log line
 * carries them and so that events produced by this work inherit them.
 *
 * <ul>
 *   <li>{@code correlationId}: the business request that started everything (HTTP request). Constant across the saga.</li>
 *   <li>{@code causationId}: the event whose processing is producing new events (the "because of").</li>
 *   <li>{@code sagaId}: the distributed workflow instance.</li>
 *   <li>{@code traceId}/{@code spanId}: the technical distributed trace, managed by Micrometer Tracing.</li>
 * </ul>
 * They are deliberately different identifiers: one request can produce several traces (asynchronous hops
 * started by schedulers), and one trace can cover several events.
 */
public final class MessageContext {

    public static final String CORRELATION_ID = CorrelationIdFilter.MDC_KEY;
    public static final String CAUSATION_ID = "causationId";
    public static final String SAGA_ID = "sagaId";
    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String EVENT_VERSION = "eventVersion";
    public static final String AGGREGATE_ID = "aggregateId";
    public static final String TOPIC = "topic";
    public static final String PARTITION = "partition";
    public static final String OFFSET = "offset";
    public static final String CONSUMER_GROUP = "consumerGroup";
    public static final String RETRY_ATTEMPT = "retryAttempt";

    private static final String[] CONSUMER_KEYS = {CORRELATION_ID, CAUSATION_ID, SAGA_ID, EVENT_ID, EVENT_TYPE,
            EVENT_VERSION, AGGREGATE_ID, TOPIC, PARTITION, OFFSET, CONSUMER_GROUP, RETRY_ATTEMPT};

    private MessageContext() {
    }

    public static String correlationId() {
        return MDC.get(CORRELATION_ID);
    }

    public static UUID causationId() {
        return uuidOrNull(MDC.get(CAUSATION_ID));
    }

    public static UUID sagaId() {
        return uuidOrNull(MDC.get(SAGA_ID));
    }

    public static void put(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }

    /** Clears everything a consumer or background job may have set. */
    public static void clearWorkContext() {
        for (String key : CONSUMER_KEYS) {
            MDC.remove(key);
        }
    }

    private static UUID uuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
