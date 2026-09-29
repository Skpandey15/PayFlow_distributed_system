package com.payflow.payment;

import com.jayway.jsonpath.JsonPath;
import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase;
import com.payflow.railsim.RailSimulator;
import com.payflow.railsim.RailSimulator.Faults;
import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WP-03 K3: a payment whose settlement outcome is unknown is escalated (WP-02), and an operator resolves it with
 * evidence from the rail. The rail simulator is ground truth: it decides whether money moved.
 */
@IntegrationTest
class ManualReviewIT {

    static final String REVIEWER = TestJwt.token("reviewer-1", "ops:manual-review payments:read");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RecoverStuckSagasUseCase recovery;
    @Autowired
    RailSimulator rail;

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

    @AfterEach
    void healRail() {
        rail.setFaults(Faults.NONE);
    }

    /** Rail keeps failing (503, never processed) until the saga is escalated to MANUAL_REVIEW. */
    UUID escalatedPayment(String amount) throws Exception {
        rail.setFaults(new Faults(0, 0, 1.0, "UNAVAILABLE_503", 64));
        UUID id = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee, amount,
                "USD", "CARD", "order-" + UUID.randomUUID()));
        await().atMost(Duration.ofSeconds(30)).until(() -> "AWAITING_SETTLEMENT".equals(api.sagaStep(id)));
        // Retries exhausted (the command is in the DLT); age the step past its timeout with the budget spent.
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() -> jdbc.queryForObject(
                "select submission_attempts from settlement.settlement where payment_id = ?", Integer.class, id) >= 3);
        jdbc.update("update payment.payment_saga set step_started_at = now() - interval '1 hour', step_attempts = 3"
                + " where payment_id = ?", id);
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            recovery.recoverOverdueSagas();
            return "MANUAL_REVIEW".equals(api.sagaStep(id));
        });
        return id;
    }

    ResultActions decide(UUID paymentId, String decision, String key) throws Exception {
        return mvc.perform(post("/api/v1/ops/manual-reviews/{id}/decisions", paymentId)
                .header("Authorization", ApiClient.bearer(REVIEWER)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"reason\":\"rail inquiry checked\",\"ticketReference\":\"OPS-42\"}"));
    }

    @Test
    void neverReceivedByTheRailIsConfirmedNotSettledAndCompensatedThroughTheNormalPath() throws Exception {
        UUID id = escalatedPayment("40.00");
        assertThat(api.accountField(payer, "$.reservedBalance")).as("funds held while unknown").isEqualTo("40.00");

        String detail = mvc.perform(get("/api/v1/ops/manual-reviews/{id}", id).header("Authorization", ApiClient.bearer(REVIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCase.escalatedFrom").value("AWAITING_SETTLEMENT"))
                .andExpect(jsonPath("$.settlement.railState").value("NOT_FOUND"))
                .andExpect(jsonPath("$.settlement.lastAttemptOutcome").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(detail, "$.allowedDecisions")).containsExactly("RESUME", "CONFIRM_NOT_SETTLED");

        String key = UUID.randomUUID().toString();
        decide(id, "CONFIRM_NOT_SETTLED", key).andExpect(status().isOk())
                .andExpect(jsonPath("$.resumedStep").value("AWAITING_SETTLEMENT"))
                .andExpect(jsonPath("$.replayed").value(false));
        decide(id, "CONFIRM_NOT_SETTLED", key).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));

        api.awaitStatus(alice, id, "FAILED");
        // The payment is FAILED when compensation starts; the saga finishes once the release is confirmed.
        await().atMost(Duration.ofSeconds(30)).until(() -> "FAILED".equals(api.sagaStep(id)));
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("100.00");
        assertThat(api.accountField(payer, "$.reservedBalance")).isEqualTo("0.00");
        assertThat(rail.recordedStatus("CARD_NETWORK", id.toString())).as("blocked at the rail").isEqualTo("DECLINED");
        assertThat(jdbc.queryForObject("select count(*) from payment.manual_review_decision where payment_id = ?",
                Integer.class, id)).as("exactly one audit record despite the replay").isEqualTo(1);
    }

    @Test
    void theRailSettledItSoFailingIsRefusedAndResumeCompletesThePayment() throws Exception {
        UUID id = escalatedPayment("25.00");
        rail.recordAccepted("CARD_NETWORK", id.toString()); // it had actually moved the money; the answer was lost
        rail.setFaults(Faults.NONE);

        mvc.perform(get("/api/v1/ops/manual-reviews/{id}", id).header("Authorization", ApiClient.bearer(REVIEWER)))
                .andExpect(jsonPath("$.settlement.railState").value("ACCEPTED"))
                .andExpect(jsonPath("$.allowedDecisions.length()").value(1));
        decide(id, "CONFIRM_NOT_SETTLED", UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RAIL_REPORTS_SETTLED"));

        decide(id, "RESUME", UUID.randomUUID().toString()).andExpect(status().isOk());
        api.awaitStatus(alice, id, "SETTLED");
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("75.00");
        assertThat(api.accountField(payer, "$.reservedBalance")).isEqualTo("0.00");
        decide(id, "RESUME", UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAGA_NOT_IN_MANUAL_REVIEW"));
    }

    @Test
    void reviewRequiresTheDedicatedScopeAndAReason() throws Exception {
        UUID someone = UUID.randomUUID();
        mvc.perform(get("/api/v1/ops/manual-reviews").header("Authorization", ApiClient.bearer(ApiClient.OPS)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ops/manual-reviews").header("Authorization", ApiClient.bearer(REVIEWER)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ops/manual-reviews/{id}/decisions", someone)
                        .header("Authorization", ApiClient.bearer(REVIEWER)).header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"RESUME\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
