package com.payflow.platform.web.traffic;

import com.payflow.platform.messaging.KafkaLagMonitor;
import com.payflow.platform.messaging.outbox.OutboxRelay;
import com.payflow.platform.messaging.outbox.OutboxRelayScheduler;
import com.payflow.platform.web.Problems;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Overload control at the HTTP edge, applied after authentication (limits are per verified subject) and before any
 * business work:
 * <ol>
 *   <li><b>Admission control (backpressure)</b> on payment creation: while the oldest unpublished outbox event is
 *       older than the admission threshold, new payments get 503 + Retry-After. Kafka being briefly down does not
 *       trip it (payments are still accepted into the outbox, WP-02 degraded mode); a sustained backlog does, before
 *       the backlog grows without bound and every accepted payment misses its completion SLO.</li>
 *   <li><b>Rate limiting</b>: 429 + Retry-After per subject on payment creation and on state-changing ops endpoints.</li>
 * </ol>
 * Reads, health and metrics are never throttled here. Every rejection is counted
 * ({@code payflow.traffic.rejected{policy}}); none is logged per request (that would itself be a flood).
 */
@Component
public class TrafficControlInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("payflow.traffic");
    private static final Duration ADMISSION_CACHE = Duration.ofSeconds(1);

    private final SubjectRateLimiters payments;
    private final SubjectRateLimiters ops;
    private final OutboxRelayScheduler outboxes;
    private final ObjectProvider<KafkaLagMonitor> lag;
    private final TrafficControlProperties properties;
    private final MeterRegistry meters;
    private final JsonMapper json;
    private final Clock clock;
    private final Semaphore inFlight;
    private volatile Instant admissionCheckedAt = Instant.EPOCH;
    private volatile boolean admissionOpen = true;

    public TrafficControlInterceptor(TrafficControlProperties properties, OutboxRelayScheduler outboxes,
                                     ObjectProvider<KafkaLagMonitor> lag,
                                     MeterRegistry meters, JsonMapper json, Clock clock) {
        this.lag = lag;
        this.properties = properties;
        this.outboxes = outboxes;
        this.meters = meters;
        this.json = json;
        this.clock = clock;
        this.payments = new SubjectRateLimiters("payments", properties.paymentsPerSubjectPerSecond(), Duration.ofSeconds(1), clock);
        this.ops = new SubjectRateLimiters("ops", properties.opsPerSubjectPerMinute(), Duration.ofMinutes(1), clock);
        this.inFlight = properties.maxConcurrentPaymentRequests() > 0
                ? new Semaphore(properties.maxConcurrentPaymentRequests()) : null;
        if (inFlight != null) {
            Gauge.builder("payflow.traffic.payments.in_flight", inFlight,
                            s -> properties.maxConcurrentPaymentRequests() - s.availablePermits())
                    .strongReference(true).register(meters);
        }
    }

    private static final String PERMIT = TrafficControlInterceptor.class.getName() + ".permit";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if ("/api/v1/payments".equals(path)) {
            if (!admissionOpen()) {
                return reject(response, "admission", HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_TEMPORARILY_UNAVAILABLE",
                        "Payment intake is paused while queued payments are processed. Retry later.",
                        properties.admissionRetryAfter());
            }
            if (!limit(response, payments, "payments")) {
                return false;
            }
            // In-flight bulkhead last, so a rejected-by-rate-limit request never takes a slot.
            if (inFlight != null) {
                if (!inFlight.tryAcquire()) {
                    return reject(response, "concurrency", HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_OVERLOADED",
                            "Too many payments in progress on this instance. Retry shortly.", Duration.ofSeconds(1));
                }
                request.setAttribute(PERMIT, Boolean.TRUE);
            }
            return true;
        }
        if (path.startsWith("/api/v1/ops/")) {
            return limit(response, ops, "ops");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (inFlight != null && request.getAttribute(PERMIT) != null) {
            request.removeAttribute(PERMIT);
            inFlight.release();
        }
    }

    private boolean limit(HttpServletResponse response, SubjectRateLimiters limiters, String policy) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || limiters.tryAcquire(auth.getName())) {
            return true;
        }
        return reject(response, policy, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many requests. Retry after the indicated delay.", limiters.retryAfter());
    }

    /** Cached for 1 s so a request burst costs one indexed query per second, not one per request. */
    private boolean admissionOpen() {
        Instant now = clock.instant();
        if (now.isAfter(admissionCheckedAt.plus(ADMISSION_CACHE))) {
            admissionCheckedAt = now;
            try {
                Duration oldest = outboxes.relays().stream().map(OutboxRelay::oldestUnpublishedAge)
                        .max(Duration::compareTo).orElse(Duration.ZERO);
                var monitor = lag.getIfAvailable();
                long consumerLag = monitor == null ? 0 : monitor.maxMainTopicLag();
                boolean open = oldest.compareTo(properties.admissionMaxOutboxAge()) <= 0
                        && (properties.admissionMaxConsumerLag() <= 0 || consumerLag <= properties.admissionMaxConsumerLag());
                if (open != admissionOpen) {
                    (open ? log.atInfo() : log.atWarn()).addKeyValue("oldestUnpublishedAgeSeconds", oldest.toSeconds())
                            .addKeyValue("maxConsumerLag", consumerLag)
                            .log(open ? "payment admission reopened" : "payment admission closed: unfinished work above threshold");
                }
                admissionOpen = open;
            } catch (RuntimeException e) {
                // Database unreachable: the create itself will fail with 503; do not add a second failure mode here.
                admissionOpen = true;
            }
        }
        return admissionOpen;
    }

    private boolean reject(HttpServletResponse response, String policy, HttpStatus status, String code, String detail,
                           Duration retryAfter) throws IOException {
        Counter.builder("payflow.traffic.rejected").tag("policy", policy).register(meters).increment();
        ProblemDetail problem = Problems.of(status, code, detail);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfter.toSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
        return false;
    }

    @Scheduled(fixedDelay = 60_000)
    void evictIdleBuckets() {
        payments.evictIdle();
        ops.evictIdle();
    }
}
