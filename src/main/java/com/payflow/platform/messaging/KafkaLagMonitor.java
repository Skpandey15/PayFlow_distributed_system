package com.payflow.platform.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Broker-side consumer lag: {@code payflow.kafka.consumer.lag{group, topic}} = log-end offset − committed offset,
 * summed over partitions.
 *
 * <p>Why not only the client metric ({@code kafka.consumer.fetch.manager.records.lag.max})? That metric exists only
 * while a consumer is alive and polling. A crashed or wedged consumer, which is exactly when lag matters, reports
 * nothing. This monitor reads committed offsets from the broker, so lag keeps growing visibly when nobody consumes.
 * Every replica reports the same group-wide value, so dashboards aggregate with {@code max}, not {@code sum}.
 */
@Component
public class KafkaLagMonitor implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(KafkaLagMonitor.class);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    private final Admin admin;
    private final MultiGauge lag;
    private final Clock clock;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final AtomicLong maxMainTopicLag = new AtomicLong();

    public KafkaLagMonitor(KafkaAdmin kafkaAdmin, MeterRegistry registry, Clock clock) {
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(AdminClientConfig.CLIENT_ID_CONFIG, "payflow-lag-monitor");
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) CALL_TIMEOUT.toMillis());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) CALL_TIMEOUT.toMillis());
        this.admin = Admin.create(config);
        this.clock = clock;
        this.lag = MultiGauge.builder("payflow.kafka.consumer.lag").description("Committed-offset lag per group and topic")
                .baseUnit("records").register(registry);
        // Staleness signal: if this stops advancing, the lag numbers above are not current (broker unreachable).
        Gauge.builder("payflow.kafka.lag.monitor.last.success", lastSuccessEpochSeconds, AtomicLong::get)
                .baseUnit("seconds").strongReference(true).register(registry);
    }

    @Scheduled(fixedDelayString = "${payflow.messaging.lag-monitor-interval-ms:15000}", initialDelay = 10000)
    public void refresh() {
        try {
            List<MultiGauge.Row<?>> rows = new ArrayList<>();
            long mainTopicMax = 0;
            for (GroupListing group : admin.listGroups(ListGroupsOptions.forConsumerGroups()).all().get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(group.groupId())
                        .partitionsToOffsetAndMetadata().get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                committed.values().removeIf(java.util.Objects::isNull);
                if (committed.isEmpty()) {
                    continue;
                }
                Map<TopicPartition, ListOffsetsResultInfo> ends = admin.listOffsets(committed.keySet().stream()
                                .collect(Collectors.toMap(Function.identity(), tp -> OffsetSpec.latest())))
                        .all().get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                Map<String, Long> byTopic = new TreeMap<>();
                committed.forEach((tp, offset) -> byTopic.merge(tp.topic(),
                        Math.max(0, ends.get(tp).offset() - offset.offset()), Long::sum));
                for (Map.Entry<String, Long> e : byTopic.entrySet()) {
                    rows.add(MultiGauge.Row.of(Tags.of("group", group.groupId(), "topic", e.getKey()), e.getValue()));
                    if (!e.getKey().contains("-")) { // main topics only; retry/DLT topics are named <topic>-<group>-...
                        mainTopicMax = Math.max(mainTopicMax, e.getValue());
                    }
                }
            }
            lag.register(rows, true);
            maxMainTopicLag.set(mainTopicMax);
            lastSuccessEpochSeconds.set(clock.instant().getEpochSecond());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Broker unreachable: keep the last values; the staleness gauge and the outbox age alert cover this case.
            log.atDebug().addKeyValue("errorCode", e.getClass().getSimpleName()).log("consumer lag refresh failed");
        }
    }

    /**
     * Largest backlog of any consumer group on a main (non-retry) topic, from the last successful read. Used by
     * admission control: once the relay stopped being the bottleneck (WP-03), unfinished work accumulates here.
     */
    public long maxMainTopicLag() {
        return maxMainTopicLag.get();
    }

    @Override
    public void destroy() {
        admin.close(Duration.ofSeconds(2));
    }
}
