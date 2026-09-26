package com.payflow.platform.messaging.consumer;

import com.payflow.contracts.EventCatalog;
import com.payflow.contracts.EventCatalog.EventDefinition;
import com.payflow.platform.messaging.EventCodec;
import com.payflow.platform.messaging.EventEnvelope;
import com.payflow.platform.messaging.MessageContext;
import com.payflow.platform.messaging.MessagingMetrics;
import com.payflow.platform.messaging.error.EventProcessingException;
import com.payflow.platform.messaging.error.FailureCategory;
import com.payflow.platform.messaging.error.FailureClassifier;
import com.payflow.platform.messaging.error.InvalidEventException;
import com.payflow.platform.messaging.error.UnsupportedEventVersionException;
import com.payflow.platform.messaging.error.UntrustedEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The asynchronous boundary shared by every Kafka inbound adapter. It is the one place that:
 * <ol>
 *   <li>treats the record as <b>untrusted input</b>: decodes it and validates the envelope, type, topic,
 *       producer, version and key</li>
 *   <li>establishes the logging and propagation context (eventId, correlationId, causationId, sagaId, topic,
 *       partition, offset, consumer group, retry attempt)</li>
 *   <li>invokes the handler, which calls an application port</li>
 *   <li><b>classifies</b> any failure and logs it <b>once</b>, then rethrows a typed exception so the retry
 *       topology can decide: retry topic or DLT</li>
 * </ol>
 * Offsets are committed by the container only after this method returns normally (AckMode.RECORD, auto-commit
 * off), which is after the business transaction has committed. Delivery is therefore at-least-once, and
 * handlers are idempotent.
 */
@Component
public class EventConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger("payflow.events.consumer");

    private final EventCodec codec;
    private final MessagingMetrics metrics;
    private final List<EventProcessingInterceptor> interceptors;

    public EventConsumerSupport(EventCodec codec, MessagingMetrics metrics, List<EventProcessingInterceptor> interceptors) {
        this.codec = codec;
        this.metrics = metrics;
        this.interceptors = interceptors;
    }

    public void process(ConsumerRecord<String, String> record, String consumer, EventHandler handler) {
        long started = System.nanoTime();
        String logicalTopic = logicalTopic(record.topic());
        int attempt = deliveryAttempt(record);
        MessageContext.put(MessageContext.TOPIC, record.topic());
        MessageContext.put(MessageContext.PARTITION, record.partition());
        MessageContext.put(MessageContext.OFFSET, record.offset());
        MessageContext.put(MessageContext.CONSUMER_GROUP, consumer);
        MessageContext.put(MessageContext.RETRY_ATTEMPT, attempt);
        try {
            EventEnvelope envelope = codec.decode(record.value());
            bindContext(envelope);
            EventDefinition definition = validate(envelope, logicalTopic, record.key());
            Object payload = codec.payload(envelope, definition);
            IncomingEvent event = new IncomingEvent(envelope, definition, payload, logicalTopic, record.partition(),
                    record.offset(), consumer, attempt);

            ConsumerOutcome outcome = handler.handle(event);
            interceptors.forEach(i -> i.afterCommit(event));

            Duration took = Duration.ofNanos(System.nanoTime() - started);
            metrics.consumed(logicalTopic, consumer, outcome.name(), took);
            log.atInfo()
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("durationMs", took.toMillis())
                    .log("event consumed");
        } catch (RuntimeException e) {
            EventProcessingException classified = FailureClassifier.classify(e);
            metrics.failed(logicalTopic, consumer, classified.category().name());
            var warn = log.atWarn()
                    .addKeyValue("failureCategory", classified.category())
                    .addKeyValue("errorCode", classified.errorCode())
                    .addKeyValue("retryable", classified.category().retryable())
                    .addKeyValue("durationMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
            if (classified.category() == FailureCategory.UNKNOWN) {
                warn = warn.setCause(e); // unclassified failures need the stack trace, internal logs only
            }
            warn.log(classified.category().retryable()
                    ? "event processing failed; scheduled for retry"
                    : "event processing failed permanently; routing to dead-letter topic");
            throw classified;
        } finally {
            MessageContext.clearWorkContext();
        }
    }

    private static void bindContext(EventEnvelope e) {
        MessageContext.put(MessageContext.EVENT_ID, e.eventId());
        MessageContext.put(MessageContext.EVENT_TYPE, e.eventType());
        MessageContext.put(MessageContext.EVENT_VERSION, e.eventVersion());
        MessageContext.put(MessageContext.AGGREGATE_ID, e.aggregateId());
        MessageContext.put(MessageContext.CORRELATION_ID, e.correlationId());
        // Anything produced while handling this event is caused by it.
        MessageContext.put(MessageContext.CAUSATION_ID, e.eventId());
        MessageContext.put(MessageContext.SAGA_ID, e.sagaId());
    }

    private static EventDefinition validate(EventEnvelope e, String logicalTopic, String key) {
        EventDefinition definition = EventCatalog.find(e.eventType())
                .orElseThrow(() -> new InvalidEventException("UNKNOWN_EVENT_TYPE", "Unknown event type", null));
        if (!definition.topic().equals(logicalTopic)) {
            throw new UntrustedEventException("EVENT_ON_WRONG_TOPIC",
                    e.eventType() + " is not allowed on " + logicalTopic);
        }
        if (!definition.producer().equals(e.producer())) {
            throw new UntrustedEventException("UNAUTHORIZED_PRODUCER",
                    e.producer() + " may not produce " + e.eventType());
        }
        if (!definition.supports(e.eventVersion())) {
            throw new UnsupportedEventVersionException(e.eventType(), e.eventVersion());
        }
        if (key != null && !key.equals(e.aggregateId())) {
            // Partitioning (and therefore ordering) relies on key == aggregate id.
            throw new InvalidEventException("KEY_AGGREGATE_MISMATCH", "Record key does not match aggregateId", null);
        }
        return definition;
    }

    /** Retry and DLT topics are named {@code <topic>-<group>-retry-N|-dlt}; the contract topic is the prefix. */
    static String logicalTopic(String physicalTopic) {
        int dash = physicalTopic.indexOf('-');
        return dash < 0 ? physicalTopic : physicalTopic.substring(0, dash);
    }

    static int deliveryAttempt(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS);
        if (header == null) {
            return 1;
        }
        byte[] value = header.value();
        if (value.length == Integer.BYTES) {
            return ByteBuffer.wrap(value).getInt();
        }
        try {
            return Integer.parseInt(new String(value, StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
