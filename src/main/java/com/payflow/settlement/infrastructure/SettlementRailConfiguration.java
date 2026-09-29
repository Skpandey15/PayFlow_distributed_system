package com.payflow.settlement.infrastructure;

import com.payflow.settlement.adapter.out.gateway.HttpSettlementRailGateway;
import com.payflow.settlement.adapter.out.gateway.RailCallMetrics;
import com.payflow.settlement.application.port.out.SettlementGatewayPort;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayUnavailableException;
import com.payflow.settlement.application.usecase.SettlementGatewayRouter;
import com.payflow.settlement.domain.SettlementRail;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

/**
 * Builds one {@link HttpSettlementRailGateway} per rail, each with its <b>own</b> circuit breaker and bulkheads:
 * an outage of the UPI switch must not stop card settlements. The registries are bound to Micrometer, so circuit
 * state, retry outcomes and bulkhead saturation are Prometheus metrics.
 *
 * <p>Deliberately programmatic, not annotation-driven: the policy is visible in one place, applies only to this
 * adapter, and the application and domain layers never see Resilience4j (enforced by ArchUnit).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SettlementRailProperties.class)
class SettlementRailConfiguration {

    private static final Logger log = LoggerFactory.getLogger("payflow.resilience");

    /** Only these failures are worth an immediate retry (see HttpSettlementRailGateway). */
    private static final Set<String> RETRYABLE_NOW = Set.of("SETTLEMENT_RAIL_UNREACHABLE", "SETTLEMENT_RAIL_UNAVAILABLE");
    /** Not the rail's fault: a full local bulkhead. Never counted against the rail's health. */
    private static final Set<String> NOT_RAIL_HEALTH = Set.of("SETTLEMENT_RAIL_BULKHEAD_FULL", "SETTLEMENT_RAIL_CIRCUIT_OPEN");

    @Bean
    CircuitBreakerRegistry settlementCircuitBreakers(SettlementRailProperties p, MeterRegistry meters) {
        var c = p.circuitBreaker();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(c.slidingWindowSize())
                .minimumNumberOfCalls(c.minimumCalls())
                .failureRateThreshold(c.failureRatePercent())
                .slowCallDurationThreshold(c.slowCallThreshold())
                .slowCallRateThreshold(c.slowCallRatePercent())
                .waitDurationInOpenState(c.openDuration())
                .permittedNumberOfCallsInHalfOpenState(c.halfOpenCalls())
                .recordException(e -> e instanceof GatewayUnavailableException g && !NOT_RAIL_HEALTH.contains(g.code()))
                .build());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    RetryRegistry settlementRetries(SettlementRailProperties p, MeterRegistry meters) {
        var r = p.retry();
        RetryRegistry registry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(r.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(r.initialBackoff(), r.multiplier(), r.jitter()))
                .retryOnException(e -> e instanceof GatewayUnavailableException g && RETRYABLE_NOW.contains(g.code()))
                .failAfterMaxAttempts(false)
                .build());
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    BulkheadRegistry settlementBulkheads(MeterRegistry meters) {
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
        TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    RailCallMetrics railCallMetrics(MeterRegistry meters) {
        return new RailCallMetrics(meters);
    }

    /** All rails' gateways behind the router, which fails startup if a rail has none (or two). */
    @Bean
    SettlementGatewayRouter settlementGatewayRouter(SettlementRailProperties p, RestClient.Builder http,
                                                       CircuitBreakerRegistry breakers, RetryRegistry retries,
                                                       BulkheadRegistry bulkheads, RailCallMetrics metrics) {
        // One JDK client (connection pool) shared by all rails; timeouts are per request factory.
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(p.connectTimeout()).build();
        RestClient submit = http.clone().baseUrl(p.baseUrl()).requestFactory(factory(client, p.submitTimeout())).build();
        RestClient inquiry = http.clone().baseUrl(p.baseUrl()).requestFactory(factory(client, p.inquiryTimeout())).build();
        return Arrays.stream(SettlementRail.values()).map(rail -> {
            String name = "settlement-rail-" + rail.name().toLowerCase().replace('_', '-');
            CircuitBreaker breaker = breakers.circuitBreaker(name);
            breaker.getEventPublisher().onStateTransition(e -> {
                // One line per state change (not per call): the operational signal an on-call engineer needs.
                var event = e.getStateTransition().getToState() == CircuitBreaker.State.OPEN ? log.atWarn() : log.atInfo();
                event.addKeyValue("dependency", name).addKeyValue("circuitBreakerState", e.getStateTransition().getToState())
                        .addKeyValue("fromState", e.getStateTransition().getFromState())
                        .log("circuit breaker state changed");
            });
            Bulkhead payments = bulkheads.bulkhead(name, BulkheadConfig.custom()
                    .maxConcurrentCalls(p.bulkhead().maxConcurrentCalls()).maxWaitDuration(Duration.ZERO).build());
            Bulkhead inquiries = bulkheads.bulkhead(name + "-inquiry", BulkheadConfig.custom()
                    .maxConcurrentCalls(p.bulkhead().inquiryMaxConcurrentCalls()).maxWaitDuration(Duration.ZERO).build());
            return (SettlementGatewayPort) new HttpSettlementRailGateway(rail, submit, inquiry, breaker,
                    retries.retry(name), payments, inquiries, metrics);
        }).collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                SettlementGatewayRouter::new));
    }

    private static JdkClientHttpRequestFactory factory(HttpClient client, Duration responseTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(responseTimeout);
        return factory;
    }
}
