package com.payflow.platform.messaging.dlq;

import com.payflow.platform.messaging.MessagingHeaders;
import com.payflow.platform.messaging.MessagingMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Consumer of every dead-letter topic. It does not "handle" the failure. It makes it visible, exactly once:
 * one ERROR log with the diagnostic coordinates (never the payload) and the {@code payflow.events.dead_lettered}
 * counter, which is the alerting signal. The record itself stays on the DLT for investigation and controlled replay.
 */
@Component
public class DeadLetterObserver {

    private static final Logger log = LoggerFactory.getLogger("payflow.events.dlq");

    private final MessagingMetrics metrics;

    public DeadLetterObserver(MessagingMetrics metrics) {
        this.metrics = metrics;
    }

    public void observe(ConsumerRecord<String, String> record, String consumer) {
        String originalTopic = text(record, KafkaHeaders.ORIGINAL_TOPIC);
        String category = text(record, MessagingHeaders.FAILURE_CATEGORY);
        metrics.deadLettered(originalTopic == null ? record.topic() : originalTopic, consumer,
                category == null ? "UNKNOWN" : category);
        log.atError()
                .addKeyValue("dltTopic", record.topic())
                .addKeyValue("dltPartition", record.partition())
                .addKeyValue("dltOffset", record.offset())
                .addKeyValue("originalTopic", originalTopic)
                .addKeyValue("originalPartition", intHeader(record, KafkaHeaders.ORIGINAL_PARTITION))
                .addKeyValue("originalOffset", longHeader(record, KafkaHeaders.ORIGINAL_OFFSET))
                .addKeyValue("consumerGroup", consumer)
                .addKeyValue("eventId", text(record, MessagingHeaders.EVENT_ID))
                .addKeyValue("eventType", text(record, MessagingHeaders.EVENT_TYPE))
                .addKeyValue("eventVersion", text(record, MessagingHeaders.EVENT_VERSION))
                .addKeyValue("correlationId", text(record, MessagingHeaders.CORRELATION_ID))
                .addKeyValue("traceparent", text(record, MessagingHeaders.TRACEPARENT))
                .addKeyValue("failureCategory", category)
                .addKeyValue("errorCode", text(record, MessagingHeaders.ERROR_CODE))
                .log("event dead-lettered; requires investigation");
    }

    static String text(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    static Integer intHeader(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null || h.value().length != Integer.BYTES ? null : ByteBuffer.wrap(h.value()).getInt();
    }

    static Long longHeader(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null || h.value().length != Long.BYTES ? null : ByteBuffer.wrap(h.value()).getLong();
    }
}
