package com.payflow.settlement;

import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase;
import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase.RecoveryAction;
import com.payflow.railsim.RailSimulator;
import com.payflow.settlement.application.port.in.SubmitSettlementUseCase;
import com.payflow.settlement.application.port.in.SubmitSettlementUseCase.Method;
import com.payflow.settlement.application.port.in.SubmitSettlementUseCase.SubmitSettlementCommand;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.TestJwt;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Review R-2: while a rail's circuit is open, its settlement commands are parked (PENDING, no retry topic, no DLT)
 * and resumed once the circuit lets calls through. Other rails keep settling meanwhile.
 */
@IntegrationTest
class SettlementParkingIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CircuitBreakerRegistry breakers;
    @Autowired
    MeterRegistry meters;
    @Autowired
    RailSimulator rail;
    @Autowired
    SubmitSettlementUseCase submit;
    @Autowired
    RecoverStuckSagasUseCase recovery;

    ApiClient api;
    String alice;
    UUID payer;
    UUID payee;

    CircuitBreaker card() {
        return breakers.circuitBreaker("settlement-rail-card-network");
    }

    double deadLetters() {
        return meters.find("payflow.events.dead_lettered").counters().stream().mapToDouble(Counter::count).sum();
    }

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        alice = TestJwt.token("alice-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        payer = api.fundedAccount(alice, "USD", "100.00");
        payee = api.openAccount(TestJwt.token("bob-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES), "USD");
    }

    @AfterEach
    void closeCircuit() {
        card().transitionToClosedState();
    }

    UUID pay(String method) throws Exception {
        return api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee, "10.00", "USD",
                method, "order-" + UUID.randomUUID()));
    }

    Map<String, Object> settlement(UUID paymentId) {
        return jdbc.queryForMap("select status, last_error_code, submission_attempts from settlement.settlement"
                + " where payment_id = ?", paymentId);
    }

    @Test
    void openCircuitParksTheSettlementAndClosingItResumesItOnce() throws Exception {
        double deadLettersBefore = deadLetters();
        card().transitionToForcedOpenState();

        UUID parked = pay("CARD");
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() ->
                "SETTLEMENT_RAIL_CIRCUIT_OPEN".equals(settlement(parked).get("last_error_code")));
        UUID otherRail = pay("BANK_TRANSFER");
        api.awaitStatus(alice, otherRail, "SETTLED");

        // Held, not failed: still PENDING a few resume cycles later, the saga still waits, nothing dead-lettered.
        Thread.sleep(3000);
        assertThat(settlement(parked).get("status")).isEqualTo("PENDING");
        assertThat(api.sagaStep(parked)).isEqualTo("AWAITING_SETTLEMENT");
        assertThat(rail.recordedStatus("CARD_NETWORK", parked.toString())).as("nothing was sent").isNull();
        assertThat(deadLetters()).isEqualTo(deadLettersBefore);

        card().transitionToClosedState();
        api.awaitStatus(alice, parked, "SETTLED");
        assertThat(settlement(parked).get("status")).isEqualTo("COMPLETED");
        assertThat(rail.recordedStatus("CARD_NETWORK", parked.toString())).isEqualTo("ACCEPTED");
        assertThat(deadLetters()).isEqualTo(deadLettersBefore);
    }

    @Test
    void aRepeatedCommandForAParkedSettlementLeavesItToTheResumer() throws Exception {
        card().transitionToForcedOpenState();
        UUID parked = pay("CARD");
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() ->
                "SETTLEMENT_RAIL_CIRCUIT_OPEN".equals(settlement(parked).get("last_error_code")));

        // Keep the resumer away from it for now (the claim needs the row idle), then close the circuit: a saga
        // re-issue arriving now must not submit directly.
        jdbc.update("update settlement.settlement set last_attempt_at = now() + interval '1 hour' where payment_id = ?", parked);
        card().transitionToClosedState();
        var result = submit.submit(new SubmitSettlementCommand(parked, Method.CARD, Money.of("10.00", "USD"), "re-issue"));
        assertThat(result.parked()).isTrue();
        assertThat(rail.recordedStatus("CARD_NETWORK", parked.toString())).as("the rail was not called").isNull();

        jdbc.update("update settlement.settlement set last_attempt_at = now() - interval '1 minute' where payment_id = ?", parked);
        api.awaitStatus(alice, parked, "SETTLED");
    }

    @Test
    void sagaRecoveryDefersAParkedSettlementInsteadOfReissuingIt() throws Exception {
        card().transitionToForcedOpenState();
        UUID parked = pay("CARD");
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() ->
                "SETTLEMENT_RAIL_CIRCUIT_OPEN".equals(settlement(parked).get("last_error_code")));
        await().atMost(Duration.ofSeconds(10)).until(() -> "AWAITING_SETTLEMENT".equals(api.sagaStep(parked)));

        // The step is long overdue (a long outage), with the retry budget already spent.
        jdbc.update("update payment.payment_saga set step_started_at = now() - interval '1 hour', step_attempts = 3"
                + " where payment_id = ?", parked);
        var report = recovery.recoverOverdueSagas();
        assertThat(report.actions()).filteredOn(a -> a.paymentId().value().equals(parked))
                .singleElement().extracting(a -> a.action()).isEqualTo(RecoveryAction.SETTLEMENT_PARKED);
        assertThat(api.sagaStep(parked)).as("not escalated to manual review: nothing was sent").isEqualTo("AWAITING_SETTLEMENT");
        assertThat(jdbc.queryForObject("select step_attempts from payment.payment_saga where payment_id = ?",
                Integer.class, parked)).as("no retry used").isEqualTo(3);

        card().transitionToClosedState();
        api.awaitStatus(alice, parked, "SETTLED");
    }
}
