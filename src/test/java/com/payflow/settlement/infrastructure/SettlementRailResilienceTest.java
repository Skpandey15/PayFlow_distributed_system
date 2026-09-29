package com.payflow.settlement.infrastructure;

import com.payflow.railsim.RailSimulator;
import com.payflow.railsim.RailSimulator.Faults;
import com.payflow.settlement.application.port.out.SettlementGatewayPort;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.DeliveryOutcome;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayInstruction;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayResponse;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayUnavailableException;
import com.payflow.settlement.application.usecase.SettlementGatewayRouter;
import com.payflow.settlement.domain.SettlementRail;
import com.payflow.shared.domain.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The settlement rail's resilience policy, exercised against the real rail simulator over real HTTP (no mocks of
 * timeouts or sockets). Small breaker/timeout values keep it fast; the production values are in application.yml
 * and justified in RESILIENCE-ARCHITECTURE.md.
 */
class SettlementRailResilienceTest {

    private RailSimulator rail;
    private SettlementGatewayRouter router;
    private CircuitBreakerRegistry breakers;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void start() throws Exception {
        rail = new RailSimulator().start(0);
        meters = new SimpleMeterRegistry();
        SettlementRailProperties p = new SettlementRailProperties("http://localhost:" + rail.port(),
                Duration.ofMillis(300), Duration.ofMillis(400), Duration.ofMillis(400),
                new SettlementRailProperties.Retry(2, Duration.ofMillis(20), 2.0, 0.5),
                new SettlementRailProperties.CircuitBreaker(10, 5, 50, Duration.ofMillis(300), 80, Duration.ofMillis(500), 2),
                new SettlementRailProperties.Bulkhead(4, 1));
        SettlementRailConfiguration config = new SettlementRailConfiguration();
        breakers = config.settlementCircuitBreakers(p, meters);
        router = config.settlementGatewayRouter(p, RestClient.builder(), breakers, config.settlementRetries(p, meters),
                config.settlementBulkheads(meters), config.railCallMetrics(meters));
    }

    @AfterEach
    void stop() {
        rail.stop();
    }

    private SettlementGatewayPort card() {
        return router.gatewayFor(SettlementRail.CARD_NETWORK);
    }

    private static GatewayInstruction instruction(String key, String reference) {
        return new GatewayInstruction(key, Money.of("10.00", "USD"), reference);
    }

    private CircuitBreaker cardBreaker() {
        return breakers.circuitBreaker("settlement-rail-card-network");
    }

    @Test
    void acceptedSubmissionIsIdempotentByKey() {
        String key = UUID.randomUUID().toString();
        GatewayResponse first = card().submit(instruction(key, "ref"));
        GatewayResponse again = card().submit(instruction(key, "ref"));
        assertThat(first.accepted()).isTrue();
        assertThat(again).as("same key, same outcome: money moves once").isEqualTo(first);
    }

    @Test
    void timeoutIsAnUnknownOutcomeNeverADeclineAndIsNotRetried() {
        String key = UUID.randomUUID().toString();
        long before = rail.submissions();
        assertThatThrownBy(() -> card().submit(instruction(key, "SIM-TIMEOUT")))
                .isInstanceOfSatisfying(GatewayUnavailableException.class, e -> {
                    assertThat(e.code()).isEqualTo("SETTLEMENT_RAIL_TIMEOUT");
                    assertThat(e.outcome()).isEqualTo(DeliveryOutcome.UNKNOWN);
                });
        assertThat(rail.submissions() - before).as("a slow provider is not hit again immediately").isEqualTo(1);
        // Ground truth: the rail DID accept it. Treating the timeout as a decline would release funds for money
        // that moved. The inquiry is how the truth is recovered.
        assertThat(rail.recordedStatus("CARD_NETWORK", key)).isEqualTo("ACCEPTED");
        assertThat(card().inquire(key)).hasValueSatisfying(r -> assertThat(r.accepted()).isTrue());
    }

    @Test
    void unavailableIsRetriedOnceWithTheSameKey() {
        long before = rail.submissions();
        assertThatThrownBy(() -> card().submit(instruction(UUID.randomUUID().toString(), "SIM-UNAVAILABLE")))
                .isInstanceOfSatisfying(GatewayUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo("SETTLEMENT_RAIL_UNAVAILABLE"));
        assertThat(rail.submissions() - before).as("1 call + 1 bounded retry, not more").isEqualTo(2);
    }

    @Test
    void unreachableRailIsNotSent() {
        rail.stop();
        assertThatThrownBy(() -> card().submit(instruction(UUID.randomUUID().toString(), "ref")))
                .isInstanceOfSatisfying(GatewayUnavailableException.class, e -> {
                    assertThat(e.code()).isEqualTo("SETTLEMENT_RAIL_UNREACHABLE");
                    assertThat(e.outcome()).isEqualTo(DeliveryOutcome.NOT_SENT);
                });
    }

    @Test
    void circuitOpensOnFailuresFailsFastThenRecoversThroughHalfOpen() {
        assertThat(cardBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        rail.setFaults(new Faults(0, 0, 1.0, "UNAVAILABLE_503", 64));
        for (int i = 0; i < 6; i++) {
            try {
                card().submit(instruction(UUID.randomUUID().toString(), "ref"));
            } catch (GatewayUnavailableException expected) {
                // counted by the breaker
            }
        }
        assertThat(cardBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        long before = rail.submissions();
        assertThatThrownBy(() -> card().submit(instruction(UUID.randomUUID().toString(), "ref")))
                .isInstanceOfSatisfying(GatewayUnavailableException.class, e -> {
                    assertThat(e.code()).isEqualTo("SETTLEMENT_RAIL_CIRCUIT_OPEN");
                    assertThat(e.outcome()).isEqualTo(DeliveryOutcome.NOT_SENT);
                });
        assertThat(rail.submissions()).as("open circuit: the failing rail is not called").isEqualTo(before);
        assertThat(breakers.circuitBreaker("settlement-rail-bank-transfer").getState())
                .as("per-rail breakers: a card outage does not stop bank transfers").isEqualTo(CircuitBreaker.State.CLOSED);

        rail.setFaults(Faults.NONE);
        assertThat(router.gatewayFor(SettlementRail.BANK_TRANSFER).submit(instruction(UUID.randomUUID().toString(), "ref"))
                .accepted()).isTrue();
        await().atMost(Duration.ofSeconds(3)).ignoreExceptions().untilAsserted(() -> {
            // After the open duration the next calls are HALF_OPEN probes; two successes close the circuit.
            card().submit(instruction(UUID.randomUUID().toString(), "ref"));
            assertThat(cardBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        });
    }

    @Test
    void slowRailOpensTheCircuitOnSlowCallsBeforeCallsTimeOut() {
        rail.setFaults(new Faults(320, 0, 0, "UNAVAILABLE_503", 64)); // slower than 300 ms threshold, under 400 ms timeout
        for (int i = 0; i < 6; i++) {
            try {
                card().submit(instruction(UUID.randomUUID().toString(), "ref"));
            } catch (GatewayUnavailableException failFast) {
                // expected once the breaker has opened on slow calls (successful but slow answers)
            }
        }
        assertThat(cardBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void bulkheadCapsConcurrentCallsToTheRailAndFailsFastAsNotSent() throws Exception {
        rail.setFaults(new Faults(250, 0, 0, "UNAVAILABLE_503", 64));
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            calls.add(() -> {
                try {
                    card().submit(instruction(UUID.randomUUID().toString(), "ref"));
                    return "OK";
                } catch (GatewayUnavailableException e) {
                    return e.code() + ":" + e.outcome();
                }
            });
        }
        List<String> results = new ArrayList<>();
        for (Future<String> f : pool.invokeAll(calls)) {
            results.add(f.get());
        }
        pool.shutdown();
        assertThat(rail.maxObservedInFlight()).as("never more than the bulkhead allows").isLessThanOrEqualTo(4);
        assertThat(results).contains("SETTLEMENT_RAIL_BULKHEAD_FULL:NOT_SENT").contains("OK");
        assertThat(cardBreaker().getState()).as("a full local bulkhead is not the rail's fault")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void voidBlocksALateInstructionButCannotUndoAnAcceptedOne() {
        String neverArrived = UUID.randomUUID().toString();
        assertThat(card().inquire(neverArrived)).isEmpty();
        assertThat(card().voidInstruction(neverArrived).declineReason()).isEqualTo("VOIDED");
        assertThat(card().submit(instruction(neverArrived, "ref")).accepted())
                .as("an instruction arriving after the void is declined, so released funds stay safe").isFalse();

        String settled = UUID.randomUUID().toString();
        card().submit(instruction(settled, "ref"));
        assertThat(card().voidInstruction(settled).accepted()).as("money already moved: void reports it").isTrue();
    }
}
