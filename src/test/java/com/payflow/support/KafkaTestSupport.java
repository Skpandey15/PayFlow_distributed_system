package com.payflow.support;

import com.payflow.platform.messaging.EnvelopeFactory;
import com.payflow.platform.messaging.OutgoingMessage;
import com.payflow.platform.messaging.PreparedMessage;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Test-side producer/reader against the real broker (no mocking of Kafka semantics). */
public record KafkaTestSupport(KafkaTemplate<String, String> kafka, ConsumerFactory<String, String> consumers,
                               EnvelopeFactory envelopes) {

    /** A correctly formed message as the owning producer would emit it. */
    public PreparedMessage prepare(String producer, OutgoingMessage message) {
        return envelopes.prepare(producer, message);
    }

    public void send(ProducerRecord<String, String> record) {
        try {
            kafka.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void sendRaw(String topic, String key, String value) {
        send(new ProducerRecord<>(topic, key, value));
    }

    /** All records currently on {@code topic} (every partition, from the beginning) matching the filter. */
    public List<ConsumerRecord<String, String>> read(String topic, Predicate<ConsumerRecord<String, String>> filter) {
        Properties props = new Properties();
        props.put("enable.auto.commit", "false");
        try (Consumer<String, String> c = consumers.createConsumer(null, "payflow-test-reader", null, props)) {
            List<PartitionInfo> partitions = c.partitionsFor(topic, Duration.ofSeconds(10));
            if (partitions == null || partitions.isEmpty()) {
                return List.of();
            }
            List<TopicPartition> tps = partitions.stream().map(p -> new TopicPartition(topic, p.partition())).toList();
            c.assign(tps);
            c.seekToBeginning(tps);
            var end = c.endOffsets(tps);
            List<ConsumerRecord<String, String>> out = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline && tps.stream().anyMatch(tp -> c.position(tp) < end.get(tp))) {
                c.poll(Duration.ofMillis(200)).forEach(r -> {
                    if (filter.test(r)) {
                        out.add(r);
                    }
                });
            }
            return out;
        }
    }

    public List<ConsumerRecord<String, String>> readKey(String topic, String key) {
        return read(topic, r -> key.equals(r.key()));
    }

    public static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
