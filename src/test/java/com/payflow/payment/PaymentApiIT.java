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

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP: controllers, validation, security, use cases, PostgreSQL and MongoDB. */
@IntegrationTest
class PaymentApiIT {

    @Autowired
    MockMvc mvc;
    ApiClient api;

    String alice;
    String bob;
    final String processor = TestJwt.token("svc-orchestrator", Actors.PROCESSOR_SCOPES);
    final String ledgerReader = TestJwt.token("svc-reconciliation", "ledger:read");
    UUID aliceUsd;
    UUID bobUsd;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        alice = TestJwt.token("alice-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        bob = TestJwt.token("bob-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        aliceUsd = api.openAccount(alice, "USD");
        bobUsd = api.openAccount(bob, "USD");
    }

    @Test
    void paymentLifecycleFromCreationToSettledLedger() throws Exception {
        var created = api.createPayment(alice, UUID.randomUUID().toString(), aliceUsd, bobUsd, "125.5", "USD", "CARD", "order-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/payments/")))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value("125.50"))
                .andExpect(jsonPath("$.currency").value("USD"));
        UUID paymentId = api.createdPaymentId(created);

        api.authorize(processor, paymentId, "US").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHORIZED"));
        api.process(processor, paymentId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SETTLED"));
        api.getPayment(alice, paymentId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SETTLED"));

        mvc.perform(get("/api/v1/ledger/accounts/{id}/balance", aliceUsd).param("currency", "USD")
                        .header("Authorization", ApiClient.bearer(ledgerReader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("-125.50"));
        mvc.perform(get("/api/v1/ledger/accounts/{id}/balance", bobUsd).param("currency", "USD")
                        .header("Authorization", ApiClient.bearer(ledgerReader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("125.50"));

        mvc.perform(get("/api/v1/payments").param("size", "5").header("Authorization", ApiClient.bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(paymentId.toString()));
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
                .andExpect(jsonPath("$.type").value("https://problems.payflow.example/idempotency-key-reused"))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    @Test
    void malformedRequestsAreRejectedWithFieldLevelProblems() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Authorization", ApiClient.bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()); // missing Idempotency-Key header

        api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd, "12.3.4", "USD", "CARD", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")));

        api.createPayment(alice, "bad key with spaces", aliceUsd, bobUsd, "1.00", "USD", "CARD", null)
                .andExpect(status().isBadRequest());

        api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd, "1.00", "USD", "CRYPTO", null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void currencyPrecisionAndBusinessRulesAreEnforced() throws Exception {
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
    void highRiskPaymentIsRejectedAndTheEvidenceIsAvailableToAnalysts() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd,
                "50.00", "USD", "CARD", null));

        api.authorize(processor, paymentId, "KP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.failureReason").value(startsWith("RISK_DECLINED")));

        mvc.perform(get("/api/v1/fraud/assessments/{id}", paymentId)
                        .header("Authorization", ApiClient.bearer(TestJwt.token("analyst-1", "fraud:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DECLINE"))
                .andExpect(jsonPath("$.signals[*].code", hasItem("HIGH_RISK_COUNTRY")))
                .andExpect(jsonPath("$.modelVersion").value("rules-v1"));
    }

    @Test
    void cancelledPaymentsCannotBeProcessedAndCancelIsIdempotent() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd,
                "5.00", "USD", "BANK_TRANSFER", null));

        api.cancel(alice, paymentId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        api.cancel(alice, paymentId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        api.process(processor, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_PROCESSABLE"));
    }

    @Test
    void settlementDeclineFailsThePaymentWithoutTouchingTheLedger() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd,
                "7.00", "USD", "BANK_TRANSFER", "SIM-DECLINE"));
        api.authorize(processor, paymentId, "US").andExpect(jsonPath("$.status").value("AUTHORIZED"));

        api.process(processor, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("SETTLEMENT_DECLINED:SIMULATED_DECLINE"));
        mvc.perform(get("/api/v1/ledger/accounts/{id}/balance", aliceUsd).param("currency", "USD")
                        .header("Authorization", ApiClient.bearer(ledgerReader)))
                .andExpect(jsonPath("$.amount").value("0.00"));
    }

    @Test
    void unknownSettlementOutcomeIsA503AndThePaymentStaysResumable() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd,
                "8.00", "USD", "CARD", "SIM-UNAVAILABLE"));
        api.authorize(processor, paymentId, "US");

        api.process(processor, paymentId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("SETTLEMENT_UNAVAILABLE"));
        api.getPayment(alice, paymentId).andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void frozenAccountsCannotBeAuthorized() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, "k-" + UUID.randomUUID(), aliceUsd, bobUsd,
                "3.00", "USD", "CARD", null));
        mvc.perform(post("/api/v1/accounts/{id}/freeze", bobUsd)
                        .header("Authorization", ApiClient.bearer(TestJwt.token("compliance-1", "accounts:admin"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"));

        api.authorize(processor, paymentId, "US")
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.failureReason").value("PAYEE_ACCOUNT_INELIGIBLE"));
    }
}
