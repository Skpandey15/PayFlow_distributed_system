package com.payflow.platform.messaging.outbox;

import com.payflow.platform.messaging.MessagingMetrics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/** Lets each context's composition root declare a relay for its own outbox table. */
@Component
public class OutboxRelayFactory {

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final KafkaTemplate<String, String> kafka;
    private final MessagingMetrics metrics;
    private final Clock clock;
    private final OutboxProperties properties;

    public OutboxRelayFactory(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                              KafkaTemplate<String, String> kafka, MessagingMetrics metrics, Clock clock,
                              OutboxProperties properties) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.kafka = kafka;
        this.metrics = metrics;
        this.clock = clock;
        this.properties = properties;
    }

    public OutboxRelay forTable(String outboxTable) {
        return new OutboxRelay(outboxTable, jdbc, transactionManager, kafka, metrics, clock, properties);
    }

    /** Same relay mechanics with a different producer, used by failure tests to simulate an unreachable broker. */
    public OutboxRelay forTable(String outboxTable, KafkaTemplate<String, String> producer) {
        return new OutboxRelay(outboxTable, jdbc, transactionManager, producer, metrics, clock, properties);
    }
}
