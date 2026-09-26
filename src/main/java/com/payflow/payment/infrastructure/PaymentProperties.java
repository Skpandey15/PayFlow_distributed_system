package com.payflow.payment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param idempotencyRetention how long an Idempotency-Key stays bound to its payment (minimum). 24h covers
 *                             client retry windows, including overnight batch retries, while keeping the table small.
 */
@ConfigurationProperties("payflow.payment")
public record PaymentProperties(
        @DefaultValue("24h") Duration idempotencyRetention) {
}
