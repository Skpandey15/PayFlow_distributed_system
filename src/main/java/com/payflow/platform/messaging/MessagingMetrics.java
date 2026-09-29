package com.payflow.platform.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event-backbone metrics. Each one answers a concrete operational question:
 * <ul>
 *   <li>{@code payflow.outbox.backlog} / {@code payflow.outbox.oldest.age}: is publication keeping up, and is Kafka reachable?</li>
 *   <li>{@code payflow.events.consumed{outcome}}: throughput, duplicates (redelivery), stale saga replies.</li>
 *   <li>{@code payflow.events.failed{category}}: which failure classes occur (retryable vs permanent).</li>
 *   <li>{@code payflow.events.dead_lettered}: something needs a human (this is the alerting metric).</li>
 *   <li>{@code payflow.events.processing}: consumer latency (p95/p99 SLOs are WP-03).</li>
 * </ul>
 * Consumer lag comes from the Kafka client metrics Spring registers ({@code kafka.consumer.fetch.manager.records.lag.max}).
 */
@Component
public class MessagingMetrics {

    private final MeterRegistry registry;

    public MessagingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void published(String outbox, int count) {
        Counter.builder("payflow.outbox.published").tag("outbox", outbox).register(registry).increment(count);
    }

    public void publishFailed(String outbox) {
        Counter.builder("payflow.outbox.publish.failures").tag("outbox", outbox).register(registry).increment();
    }

    /** Strong references: Micrometer gauges otherwise hold their state weakly and silently turn into NaN. */
    public void outboxGauges(String outbox, AtomicLong backlog, AtomicLong oldestAgeSeconds) {
        Gauge.builder("payflow.outbox.backlog", backlog, AtomicLong::get).tag("outbox", outbox)
                .strongReference(true).register(registry);
        Gauge.builder("payflow.outbox.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get).tag("outbox", outbox)
                .strongReference(true).register(registry);
    }

    public void consumed(String topic, String consumer, String outcome, Duration duration) {
        Counter.builder("payflow.events.consumed").tag("topic", topic).tag("consumer", consumer).tag("outcome", outcome)
                .register(registry).increment();
        Timer.builder("payflow.events.processing").tag("topic", topic).tag("consumer", consumer)
                .register(registry).record(duration);
    }

    public void failed(String topic, String consumer, String category) {
        Counter.builder("payflow.events.failed").tag("topic", topic).tag("consumer", consumer).tag("category", category)
                .register(registry).increment();
    }

    public void deadLettered(String topic, String consumer, String category) {
        Counter.builder("payflow.events.dead_lettered").tag("topic", topic).tag("consumer", consumer)
                .tag("category", category).register(registry).increment();
    }

    public void replayed(String topic) {
        Counter.builder("payflow.events.replayed").tag("topic", topic).register(registry).increment();
    }

    /** Latency timer; percentile histograms are switched on per name prefix in {@code management.metrics.distribution}. */
    public void time(String name, Duration duration, String... tags) {
        Timer.builder(name).tags(tags).register(registry).record(duration);
    }

    public void count(String name, String... tags) {
        Counter.builder(name).tags(tags).register(registry).increment();
    }

    public double counterValue(String name, String... tags) {
        Counter c = registry.find(name).tags(tags).counter();
        return c == null ? 0 : c.count();
    }
}
