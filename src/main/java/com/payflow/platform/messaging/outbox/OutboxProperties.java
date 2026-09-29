package com.payflow.platform.messaging.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param batchSize               rows per relay transaction
 * @param sendTimeout             max wait for a broker acknowledgement per record
 * @param relayTransactionTimeout upper bound of one relay transaction (batch × send timeout must fit)
 * @param retention               how long published rows are kept for diagnostics before purge (the events stay on
 *                                Kafka for the topic retention; the outbox copy is only for troubleshooting)
 * @param pipelined               send a batch asynchronously and await all acks once (WP-03) instead of one
 *                                synchronous send per row (WP-02); switchable for A/B measurement
 * @param drainBudget             keep publishing full batches back to back for up to this long per poll, instead
 *                                of waiting a poll interval between full batches
 */
@ConfigurationProperties("payflow.messaging.outbox")
public record OutboxProperties(
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration sendTimeout,
        @DefaultValue("30s") Duration relayTransactionTimeout,
        @DefaultValue("7d") Duration retention,
        @DefaultValue("true") boolean pipelined,
        @DefaultValue("1s") Duration drainBudget) {
}
