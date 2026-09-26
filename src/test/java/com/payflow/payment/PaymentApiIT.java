package com.payflow.payment;

import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end through HTTP and the event backbone: controllers, security, outbox relay, Kafka, every saga
 * participant (Fraud/MongoDB, Account funds, Settlement, Ledger) and back. Payments are processed
 * asynchronously, so assertions wait for the saga to reach its terminal state.
 */
@IntegrationTest
class PaymentApiIT {

    @Autowired
    MockMvc mvc;
    ApiClient api;

    String alice;
    String bob;
    UUID aliceUsd;
    UUID bobUsd;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        alice = TestJwt.token("alice-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        bob = TestJwt.token("bob-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        aliceUsd = api.fundedAccount(alice, "USD", "500.00");
        bobUsd = api.openAccount(bob, "USD");
    }

    @Test
    void sagaSettlesThePaymentMovesFundsExactlyOnceAndPostsTheLedger() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), aliceUsd, bobUsd,
                        "125.5", "USD", "CARD", "order-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/payments/")))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value("125.50")));

        api.awaitStatus(alice, paymentId, "SETTLED");

        assertThat(api.sagaStep(paymentId)).isEqualTo("COMPLETED");
        assertThat(api.accountField(aliceUsd, "$.availableBalance")).isEqualTo("374.50");
        assertThat(api.accountField(aliceUsd, "$.reservedBalance")).isEqualTo("0.00");
        assertThat(api.accountField(bobUsd, "$.availableBalance")).isEqualTo("125.50");
        // Ledger is an eventually consistent follower of funds.events; it must converge to the same numbers.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(api.ledgerBalance(aliceUsd, "USD")).isEqualTo("374.50");
            assertThat(api.ledgerBalance(bobUsd, "USD")).isEqualTo("125.50");
        });
        mvc.perform(get("/api/v1/payments").param("size", "5").header("Authorization", ApiClient.bearer(alice)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(paymentId.toString()));
    }

    @Test
    void insufficientFundsRejectsThePaymentWithoutHoldingAnything() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), aliceUsd, bobUsd,
                "500.01", "USD", "CARD", null));

        api.awaitStatus(alice, paymentId, "REJECTED");

        assertThat(api.paymentField(alice, paymentId, "$.failureReason")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(api.accountField(aliceUsd, "$.availableBalance")).isEqualTo("500.00");
        assertThat(api.accountField(aliceUsd, "$.reservedBalance")).isEqualTo("0.00");
    }

    @Test
    void highRiskPaymentIsRejectedBeforeAnyFundsAreReserved() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), aliceUsd, bobUsd,
                "50.00", "USD", "CARD", null, "KP"));

        api.awaitStatus(alice, paymentId, "REJECTED");

        assertThat(api.paymentField(alice, paymentId, "$.failureReason")).startsWith("RISK_DECLINED");
        assertThat(api.accountField(aliceUsd, "$.reservedBalance")).isEqualTo("0.00");
        mvc.perform(get("/api/v1/fraud/assessments/{id}", paymentId)
                        .header("Authorization", ApiClient.bearer(TestJwt.token("analyst-1", "fraud:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DECLINE"))
                .andExpect(jsonPath("$.signals[*].code", hasItem("HIGH_RISK_COUNTRY")))
                .andExpect(jsonPath("$.channel.countryCode").value("KP"));
    }

    @Test
    void settlementDeclineCompensatesByReleasingTheReservedFunds() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), aliceUsd, bobUsd,
                "70.00", "USD", "BANK_TRANSFER", "SIM-DECLINE"));

        api.awaitStatus(alice, paymentId, "FAILED");
        await().atMost(Duration.ofSeconds(20)).until(() -> "FAILED".equals(api.sagaStep(paymentId)));

        assertThat(api.paymentField(alice, paymentId, "$.failureReason")).isEqualTo("SETTLEMENT_DECLINED:SIMULATED_DECLINE");
        assertThat(api.accountField(aliceUsd, "$.availableBalance")).isEqualTo("500.00");
        assertThat(api.accountField(aliceUsd, "$.reservedBalance")).isEqualTo("0.00");
        assertThat(api.accountField(bobUsd, "$.availableBalance")).isEqualTo("0.00");
        assertThat(api.ledgerBalance(aliceUsd, "USD")).isEqualTo("500.00");
    }

    @Test
    void inactivePayeeIsRejectedSynchronouslyAtCreation() throws Exception {
        mvc.perform(post("/api/v1/accounts/{id}/freeze", bobUsd)
                        .header("Authorization", ApiClient.bearer(TestJwt.token("compliance-1", "accounts:admin"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"));

        api.createPayment(alice, UUID.randomUUID().toString(), aliceUsd, bobUsd, "3.00", "USD", "CARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYEE_ACCOUNT_INACTIVE"));
    }

    @Test
    void settledPaymentsCannotBeCancelled() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), aliceUsd, bobUsd,
                "5.00", "USD", "CARD", null));
        api.awaitStatus(alice, paymentId, "SETTLED");

        api.cancel(alice, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CANCELLABLE"));
    }

    @Test
    void idempotentReplayAndKeyReuseOverHttp() throws Exception {
        String key = "checkout-" + UUID.randomUUID();
        UUID first = api.createdPaymentId(api.createPayment(alice, key, aliceUsd, bobUsd, "10.00", "USD", "CARD", null)
                .andExpect(status().isCreated()));

        api.createPayment(alice, key, aliceUsd, bobUsd, "10.00", "USD", "CARD", null)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(first.toString()));

        api.createPayment(alice, key, aliceUsd, bobUsd, "10.01", "USD", "CARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.correlationId", notNullValue()));

        api.awaitStatus(alice, first, "SETTLED");
        assertThat(api.accountField(aliceUsd, "$.availableBalance")).as("charged once").isEqualTo("490.00");
    }

    @Test
    void malformedRequestsAreRejectedWithFieldLevelProblems() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Authorization", ApiClient.bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd, "12.3.4", "USD", "CARD", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")));

        api.createPayment(alice, "bad key with spaces", aliceUsd, bobUsd, "1.00", "USD", "CARD", null)
                .andExpect(status().isBadRequest());

        api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd, "1.00", "USD", "CARD", null, "Korea")
                .andExpect(status().isBadRequest());
    }

    @Test
    void currencyPrecisionAndBusinessRulesAreEnforcedSynchronously() throws Exception {
        api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd, "1.005", "USD", "CARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MONEY_PRECISION_EXCEEDED"));

        api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, aliceUsd, "1.00", "USD", "CARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_SAME_ACCOUNT"));

        api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd, "1.00", "EUR", "CARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void depositsAreIdempotentPerDepositId() throws Exception {
        UUID depositId = UUID.randomUUID();
        api.deposit(bobUsd, depositId, "20.00", "USD").andExpect(status().isCreated());
        api.deposit(bobUsd, depositId, "20.00", "USD").andExpect(status().isOk());
        api.deposit(bobUsd, depositId, "21.00", "USD")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEPOSIT_ID_REUSED"));
        assertThat(api.accountField(bobUsd, "$.availableBalance")).isEqualTo("20.00");
    }
}
