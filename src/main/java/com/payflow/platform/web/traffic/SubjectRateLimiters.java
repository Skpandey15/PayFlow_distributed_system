package com.payflow.platform.web.traffic;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One token bucket (Resilience4j {@link RateLimiter}) per authenticated subject for one policy. Buckets idle for
 * longer than {@link #IDLE_EVICTION} are dropped, so memory is bounded by active callers, not by every subject
 * ever seen. Limits are per instance: with N replicas a subject gets up to N × the limit (documented; a shared
 * limiter belongs in the API gateway).
 */
class SubjectRateLimiters {

    static final Duration IDLE_EVICTION = Duration.ofMinutes(10);

    private record Bucket(RateLimiter limiter, Instant lastUsed) {
    }

    private final String policy;
    private final RateLimiterConfig config;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    SubjectRateLimiters(String policy, int permits, Duration period, Clock clock) {
        this.policy = policy;
        this.clock = clock;
        this.config = RateLimiterConfig.custom().limitForPeriod(permits).limitRefreshPeriod(period)
                .timeoutDuration(Duration.ZERO) // never queue a request: reject immediately with 429
                .build();
    }

    boolean tryAcquire(String subject) {
        Instant now = clock.instant();
        Bucket bucket = buckets.compute(subject, (s, b) -> new Bucket(
                b == null ? RateLimiter.of(policy + ":" + s, config) : b.limiter(), now));
        return bucket.limiter().acquirePermission();
    }

    Duration retryAfter() {
        return config.getLimitRefreshPeriod();
    }

    void evictIdle() {
        Instant cutoff = clock.instant().minus(IDLE_EVICTION);
        buckets.values().removeIf(b -> b.lastUsed().isBefore(cutoff));
    }

    int size() {
        return buckets.size();
    }
}
