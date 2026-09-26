package com.payflow.payment;

import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase;
import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase.RecoveryAction;
import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.KafkaTestSupport;
import com.payflow.support.TestJwt;
import com.payflow.platform.messaging.EnvelopeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saga failure engineering: compensation, cancellation races, a check-then-act gap, participant outages
 * (MongoDB and PostgreSQL containers are really paused), and stuck-workflow recovery, including the one case
 * that must NOT be auto-compensated (unknown settlement outcome).
 */
@IntegrationTest
class SagaFailureIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    KafkaListenerEndpointRegistry listeners;
    @Autowired
    RecoverStuckSagasUseCase recovery;
    @Autowired
    MongoDBContainer mongo;
    @Autowired
    PostgreSQLContainer postgres;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    ConsumerFactory<String, String> consumerFactory;
    @Autowired
    EnvelopeFactory envelopes;

    ApiClient api;
    String alice;
    UUID payer;
    UUID payee;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        alice = TestJwt.token("alice-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        payer = api.fundedAccount(alice, "USD", "100.00");
        payee = api.openAccount(TestJwt.token("bob-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES), "USD");
    }

    UUID pay(String amount, String method, String reference) throws Exception {
        return api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee, amount, "USD",
                method, reference));
    }

    void awaitSagaStep(UUID paymentId, String step) {
        await().atMost(Duration.ofSeconds(30)).until(() -> step.equals(api.sagaStep(paymentId)));
    }

    /** Makes the saga's current step look overdue (optionally with retries already spent). */
    void makeOverdue(UUID paymentId, int attemptsAlreadyMade) {
        jdbc.update("update payment.payment_saga set step_started_at = now() - interval '1 hour', step_attempts = ?"
                + " where payment_id = ?", attemptsAlreadyMade, paymentId);
    }

    RecoveryAction recoverAndGetAction(UUID paymentId) {
        return recovery.recoverOverdueSagas().actions().stream()
                .filter(a -> a.paymentId().value().equals(paymentId)).findFirst().orElseThrow().action();
    }

    void withStopped(String listenerId, ThrowingRunnable during) throws Exception {
        MessageListenerContainer container = listeners.getListenerContainer(listenerId);
        container.stop();
        try {
            during.run();
        } finally {
            container.start();
        }
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Failure 16: the rail declines and held funds are compensated (released), not rolled back. */
    @Test
    void settlementDeclineReleasesHeldFunds() throws Exception {
        UUID paymentId = pay("60.00", "CARD", "SIM-DECLINE");
        awaitSagaStep(paymentId, "FAILED");
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("100.00");
        assertThat(api.accountField(payer, "$.reservedBalance")).isEqualTo("0.00");
        assertThat(jdbc.queryForObject("select status from account.funds_reservation where payment_id = ?",
                String.class, paymentId)).isEqualTo("RELEASED");
    }

    /** Cancellation races an in-flight reservation: compensation always wins, and the late FundsReserved reply is stale. */
    @Test
    void cancelWhileReservationIsInFlightReleasesTheFunds() throws Exception {
        UUID[] id = new UUID[1];
        withStopped("funds-commands", () -> {
            id[0] = pay("40.00", "CARD", null);
            awaitSagaStep(id[0], "AWAITING_FUNDS"); // ReserveFunds is on the topic, not yet consumed
            api.cancel(alice, id[0]).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            assertThat(api.sagaStep(id[0])).isEqualTo("COMPENSATING");
        });
        // Same partition, so ReserveFunds is processed before ReleaseFunds: reserve, then release.
        awaitSagaStep(id[0], "CANCELLED");
        assertThat(api.paymentField(alice, id[0], "$.status")).isEqualTo("CANCELLED");
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("100.00");
        assertThat(api.accountField(payer, "$.reservedBalance")).isEqualTo("0.00");
    }

    /** Check-then-act gap: the payee is frozen after creation but before reservation; the participant re-checks. */
    @Test
    void accountFrozenAfterCreationIsCaughtAtReservation() throws Exception {
        UUID[] id = new UUID[1];
        withStopped("funds-commands", () -> {
            id[0] = pay("10.00", "CARD", null);
            awaitSagaStep(id[0], "AWAITING_FUNDS");
            mvc.perform(post("/api/v1/accounts/{id}/freeze", payee)
                            .header("Authorization", ApiClient.bearer(TestJwt.token("compliance-1", "accounts:admin"))))
                    .andExpect(status().isOk());
        });
        api.awaitStatus(alice, id[0], "REJECTED");
        assertThat(api.paymentField(alice, id[0], "$.failureReason")).isEqualTo("PAYEE_ACCOUNT_INELIGIBLE");
        assertThat(api.accountField(payer, "$.reservedBalance")).isEqualTo("0.00");
    }

    /** Failure 10 + M3: the fraud store is down, commands exhaust retries into the DLT, and saga recovery re-issues after recovery. */
    @Test
    void fraudStoreOutageIsRecoveredByTheSagaScanner() throws Exception {
        UUID paymentId;
        pause(mongo);
        try {
            paymentId = pay("10.00", "CARD", null);
            KafkaTestSupport k = new KafkaTestSupport(kafka, consumerFactory, envelopes);
            await().atMost(Duration.ofSeconds(60))
                    .until(() -> !k.readKey("fraud.commands-fraud-service-dlt", paymentId.toString()).isEmpty());
            assertThat(api.paymentField(alice, paymentId, "$.status")).as("fail closed").isEqualTo("CREATED");
            assertThat(api.sagaStep(paymentId)).isEqualTo("AWAITING_RISK");
        } finally {
            unpause(mongo);
        }
        makeOverdue(paymentId, 0);
        assertThat(recoverAndGetAction(paymentId)).isEqualTo(RecoveryAction.COMMAND_REISSUED);
        api.awaitStatus(alice, paymentId, "SETTLED");
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("90.00");
    }

    /** Failure 11: PostgreSQL is paused mid-workflow; bounded retries plus recovery converge after it returns. */
    @Test
    void postgresOutageMidWorkflowConverges() throws Exception {
        UUID paymentId = pay("15.00", "BANK_TRANSFER", null);
        pause(postgres);
        try {
            Thread.sleep(3000); // consumers, relay and scanner fail against a frozen database meanwhile
        } finally {
            unpause(postgres);
        }
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).ignoreExceptions().until(() -> {
            if (!"SETTLED".equals(api.paymentField(alice, paymentId, "$.status"))) {
                makeOverdue(paymentId, 0); // whatever step lost its message to the DLT gets re-issued
                recovery.recoverOverdueSagas();
                return false;
            }
            return true;
        });
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("85.00");
        assertThat(jdbc.queryForObject("select count(*) from settlement.settlement where payment_id = ?",
                Integer.class, paymentId)).isEqualTo(1);
    }

    /** Timeout while funds may be held: reject, and compensate with a (tombstone-safe) release. */
    @Test
    void fundsReservationTimeoutRejectsAndCompensates() throws Exception {
        UUID[] id = new UUID[1];
        withStopped("funds-commands", () -> {
            id[0] = pay("25.00", "CARD", null);
            awaitSagaStep(id[0], "AWAITING_FUNDS");
            makeOverdue(id[0], 3);
            assertThat(recoverAndGetAction(id[0])).isEqualTo(RecoveryAction.COMPENSATION_STARTED);
            assertThat(api.paymentField(alice, id[0], "$.status")).isEqualTo("REJECTED");
        });
        awaitSagaStep(id[0], "REJECTED");
        assertThat(api.paymentField(alice, id[0], "$.failureReason")).isEqualTo("FUNDS_RESERVATION_TIMEOUT");
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("100.00");
        assertThat(api.accountField(payer, "$.reservedBalance")).isEqualTo("0.00");
    }

    /** Unknown settlement outcome: never auto-compensate (money may have moved); escalate and keep the hold. */
    @Test
    void unknownSettlementOutcomeIsEscalatedNotCompensated() throws Exception {
        UUID paymentId = pay("30.00", "CARD", "SIM-UNAVAILABLE");
        awaitSagaStep(paymentId, "AWAITING_SETTLEMENT");
        makeOverdue(paymentId, 0);
        assertThat(recoverAndGetAction(paymentId)).as("first: re-issue").isEqualTo(RecoveryAction.COMMAND_REISSUED);

        makeOverdue(paymentId, 3);
        assertThat(recoverAndGetAction(paymentId)).isEqualTo(RecoveryAction.ESCALATED_TO_MANUAL_REVIEW);
        assertThat(api.sagaStep(paymentId)).isEqualTo("MANUAL_REVIEW");
        assertThat(api.paymentField(alice, paymentId, "$.status")).isEqualTo("PROCESSING");
        assertThat(api.accountField(payer, "$.reservedBalance")).as("funds stay held for the investigator").isEqualTo("30.00");
    }

    private static void pause(GenericContainer<?> container) {
        DockerClientFactory.instance().client().pauseContainerCmd(container.getContainerId()).exec();
    }

    private static void unpause(GenericContainer<?> container) {
        DockerClientFactory.instance().client().unpauseContainerCmd(container.getContainerId()).exec();
    }
}
