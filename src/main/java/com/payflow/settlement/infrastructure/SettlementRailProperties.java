package com.payflow.settlement.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Resilience policy of the settlement rails. The reasoning behind every value is in
 * docs/architecture/RESILIENCE-ARCHITECTURE.md; the values here are the defaults that document derives.
 *
 * @param baseUrl         rail endpoint (the lab's rail simulator; a provider URL in production)
 * @param connectTimeout  TCP connect budget. Local network: a healthy connect takes well under 50 ms.
 * @param submitTimeout   response budget for a submission: above the rail's normal p99 with margin, far below the
 *                        consumer's poll-interval budget. Expiry means UNKNOWN outcome, never decline.
 * @param inquiryTimeout  response budget for operator inquiry/void calls (a human is waiting)
 */
@ConfigurationProperties("payflow.settlement.rail")
public record SettlementRailProperties(
        @DefaultValue("http://localhost:8090") String baseUrl,
        @DefaultValue("500ms") Duration connectTimeout,
        @DefaultValue("2s") Duration submitTimeout,
        @DefaultValue("1s") Duration inquiryTimeout,
        @DefaultValue Retry retry,
        @DefaultValue CircuitBreaker circuitBreaker,
        @DefaultValue Bulkhead bulkhead) {

    /**
     * @param maxAttempts     total attempts per submission inside one consumer delivery (1 = no retry)
     * @param initialBackoff  first backoff; doubles per attempt
     * @param jitter          randomization factor: backoff is spread over ±jitter, so a fleet of consumers that failed
     *                        together does not retry in lockstep
     */
    public record Retry(@DefaultValue("2") int maxAttempts, @DefaultValue("200ms") Duration initialBackoff,
                        @DefaultValue("2.0") double multiplier, @DefaultValue("0.5") double jitter) {
    }

    /**
     * @param slidingWindowSize         calls evaluated (count-based)
     * @param minimumCalls              no decision before this many calls (avoids opening on 1 failure of 2)
     * @param failureRatePercent        open at this failure rate
     * @param slowCallThreshold         a call slower than this is "slow" (below submitTimeout: detect degradation
     *                                  before calls start timing out)
     * @param slowCallRatePercent       open when this share of calls is slow
     * @param openDuration              fail fast this long before probing
     * @param halfOpenCalls             probe calls allowed in HALF_OPEN
     */
    public record CircuitBreaker(@DefaultValue("20") int slidingWindowSize, @DefaultValue("10") int minimumCalls,
                                 @DefaultValue("50") float failureRatePercent,
                                 @DefaultValue("1500ms") Duration slowCallThreshold,
                                 @DefaultValue("80") float slowCallRatePercent,
                                 @DefaultValue("15s") Duration openDuration, @DefaultValue("3") int halfOpenCalls) {
    }

    /**
     * @param maxConcurrentCalls         per rail, payment traffic: the provider's contracted concurrency
     * @param inquiryMaxConcurrentCalls  per rail, operator inquiries and voids (separate compartment)
     */
    public record Bulkhead(@DefaultValue("16") int maxConcurrentCalls, @DefaultValue("2") int inquiryMaxConcurrentCalls) {
    }
}
