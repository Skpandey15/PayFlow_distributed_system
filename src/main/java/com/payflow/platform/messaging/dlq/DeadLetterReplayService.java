package com.payflow.platform.messaging.dlq;

import com.payflow.platform.messaging.MessagingHeaders;
import com.payflow.platform.messaging.MessagingMetrics;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.UnprocessableException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controlled replay of one dead-lettered record.
 *
 * <p>The record is re-published to the failed consumer group's <b>own first retry topic</b>
 * ({@code <topic>-<group>-retry-0}), not to the original topic. Only the consumer that failed reprocesses it,
 * and other groups on the same topic are not disturbed. The original {@code eventId} is preserved, so the
 * consumer's inbox guarantees the business effect happens at most once, even if the record is replayed twice
 * or had in fact been applied before the failure.
 */
@Component
public class DeadLetterReplayService {

    private static final Logger log = LoggerFactory.getLogger("payflow.events.dlq");
    private static final Pattern DLT = Pattern.compile("^([a-z.]+)-([a-z-]+)-dlt$");

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafka;
    private final MessagingMetrics metrics;

    public DeadLetterReplayService(ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafka,
                                   MessagingMetrics metrics) {
        this.consumerFactory = consumerFactory;
        this.kafka = kafka;
        this.metrics = metrics;
    }

    public record ReplayResult(String replayedTo, String eventId, String eventType) {
    }

    public ReplayResult replay(String dltTopic, int partition, long offset, String requestedBy) {
        Matcher m = DLT.matcher(dltTopic);
        if (!m.matches()) {
            throw new UnprocessableException("NOT_A_DEAD_LETTER_TOPIC", "Not a PayFlow dead-letter topic: " + dltTopic);
        }
        String target = m.group(1) + "-" + m.group(2) + "-retry-0";
        ConsumerRecord<String, String> dead = fetch(dltTopic, partition, offset);

        RecordHeaders headers = new RecordHeaders();
        for (Header h : dead.headers()) {
            if (h.key().startsWith("payflow-") || h.key().equals(MessagingHeaders.TRACEPARENT)) {
                headers.add(h.key(), h.value());
            }
        }
        headers.remove(MessagingHeaders.FAILURE_CATEGORY);
        headers.remove(MessagingHeaders.ERROR_CODE);
        headers.add(MessagingHeaders.REPLAYED_FROM,
                (dltTopic + ":" + partition + ":" + offset).getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(new ProducerRecord<>(target, null, dead.key(), dead.value(), headers)).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while replaying", e);
        } catch (Exception e) {
            throw new IllegalStateException("Replay publish failed", e);
        }
        String eventId = DeadLetterObserver.text(dead, MessagingHeaders.EVENT_ID);
        String eventType = DeadLetterObserver.text(dead, MessagingHeaders.EVENT_TYPE);
        metrics.replayed(m.group(1));
        log.atInfo()
                .addKeyValue("dltTopic", dltTopic).addKeyValue("dltPartition", partition).addKeyValue("dltOffset", offset)
                .addKeyValue("replayedTo", target).addKeyValue("eventId", eventId).addKeyValue("eventType", eventType)
                .addKeyValue("requestedBy", requestedBy)
                .log("dead letter replayed");
        return new ReplayResult(target, eventId, eventType);
    }

    private ConsumerRecord<String, String> fetch(String topic, int partition, long offset) {
        Properties overrides = new Properties();
        overrides.put("enable.auto.commit", "false");
        overrides.put("max.poll.records", "1");
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(null, "payflow-dlq-replay", null, overrides)) {
            TopicPartition tp = new TopicPartition(topic, partition);
            consumer.assign(List.of(tp));
            consumer.seek(tp, offset);
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (r.offset() == offset) {
                        return r;
                    }
                    if (r.offset() > offset) {
                        throw notFound(topic, partition, offset);
                    }
                }
            }
        }
        throw notFound(topic, partition, offset);
    }

    private static NotFoundException notFound(String topic, int partition, long offset) {
        return new NotFoundException("DEAD_LETTER_NOT_FOUND",
                "No dead letter at " + topic + ":" + partition + ":" + offset);
    }
}
