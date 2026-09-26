package com.payflow.platform.messaging.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param batchSize               rows per relay transaction
 * @param sendTimeout             max wait for a broker acknowledgement per record
 * @param relayTransactionTimeout upper bound of one relay transaction (batch × send timeout must fit)
 * @param retention               how long published rows are kept for diagnostics before purge
 */
@ConfigurationProperties("payflow.messaging.outbox")
public record OutboxProperties(
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration sendTimeout,
        @DefaultValue("30s") Duration relayTransactionTimeout,
        @DefaultValue("7d") Duration retention) {
}
