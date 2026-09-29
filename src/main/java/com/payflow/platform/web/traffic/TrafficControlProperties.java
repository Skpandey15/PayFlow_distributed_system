package com.payflow.platform.web.traffic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Edge traffic policy (docs/architecture/RESILIENCE-ARCHITECTURE.md, "Rate limiting" and "Backpressure").
 *
 * @param paymentsPerSubjectPerSecond fairness limit per authenticated caller on POST /api/v1/payments. A human
 *                                    customer never approaches it; a runaway script or a compromised token does.
 * @param opsPerSubjectPerMinute      per operator on state-changing ops endpoints (DLT replay, manual-review
 *                                    decisions, reconciliation runs): protects against scripted mass actions
 * @param admissionMaxOutboxAge       stop accepting new payments (503) while the oldest unpublished event is older
 *                                    than this: every new payment would already miss the completion SLO, and
 *                                    accepting more only deepens the backlog
 * @param admissionRetryAfter         Retry-After sent with that 503
 * @param admissionMaxConsumerLag      also stop accepting new payments while any consumer group is this many records
 *                                    behind on a main topic (0 = off). After the relay fix the unfinished work
 *                                    accumulates as consumer lag, not outbox age (TUNING-RESULTS.md, step 4).
 * @param maxConcurrentPaymentRequests in-flight bulkhead for payment acceptance (0 = off). With virtual threads
 *                                    nothing else bounds how many requests wait for a database connection; WP-03
 *                                    stress showed 1,292 of them parked on the pool, the heap filling, and a 38 s
 *                                    stop-the-world GC. Excess requests now get an immediate 503 instead.
 */
@ConfigurationProperties("payflow.traffic")
public record TrafficControlProperties(
        @DefaultValue("20") int paymentsPerSubjectPerSecond,
        @DefaultValue("10") int opsPerSubjectPerMinute,
        @DefaultValue("60s") Duration admissionMaxOutboxAge,
        @DefaultValue("30s") Duration admissionRetryAfter,
        @DefaultValue("0") int maxConcurrentPaymentRequests,
        @DefaultValue("0") long admissionMaxConsumerLag) {
}
