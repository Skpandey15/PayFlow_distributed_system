package com.payflow.reconciliation;

import com.payflow.reconciliation.application.port.in.ReconciliationUseCase;
import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WP-03 K4: reconciliation detects drift between independently written financial records, classifies it, confirms
 * it across runs, and never changes the financial data itself.
 */
@IntegrationTest
class ReconciliationIT {

    static final String RECONCILER = TestJwt.token("recon-1", "ops:reconciliation");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ReconciliationUseCase reconciliation;

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
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee,
                "30.00", "USD", "CARD", null));
        api.awaitStatus(alice, paymentId, "SETTLED");
        await().atMost(Duration.ofSeconds(20)).until(() -> "70.00".equals(api.ledgerBalance(payer, "USD")));
        // Treat these accounts as "settled" (older than the grace period) so eventually consistent checks apply.
        jdbc.update("update account.account_balance set updated_at = now() - interval '1 hour' where account_id in (?, ?)",
                payer, payee);
    }

    List<Map<String, Object>> openFor(UUID subject) {
        return jdbc.queryForList("select check_name, severity, times_seen, expected, actual from reconciliation.mismatch "
                + "where subject_id = ? and status = 'OPEN'", subject.toString());
    }

    @Test
    void consistentBooksProduceNoFindingForTheseAccounts() {
        reconciliation.run();
        assertThat(openFor(payer)).isEmpty();
        assertThat(openFor(payee)).isEmpty();
    }

    @Test
    void balanceDriftIsDetectedConfirmedAndNeverRepaired() throws Exception {
        // Drift: someone changed the balance outside the domain (bad migration, manual SQL). The ledger says 70.00.
        jdbc.update("update account.account_balance set available = available + 10 where account_id = ?", payer);

        reconciliation.run();
        assertThat(openFor(payer)).singleElement().satisfies(m -> {
            assertThat(m.get("check_name")).isEqualTo("LEDGER_MATCHES_BALANCE");
            assertThat(m.get("severity")).isEqualTo("CRITICAL");
            assertThat((BigDecimal) m.get("expected")).isEqualByComparingTo("80.00");
            assertThat((BigDecimal) m.get("actual")).isEqualByComparingTo("70.00");
            assertThat(m.get("times_seen")).isEqualTo(1);
        });
        reconciliation.run();
        assertThat(openFor(payer)).singleElement().extracting(m -> m.get("times_seen"))
                .as("seen in two runs = confirmed drift (alerting threshold)").isEqualTo(2);
        assertThat(jdbc.queryForObject("select available from account.account_balance where account_id = ?",
                BigDecimal.class, payer)).as("reconciliation reports; it does not overwrite either side")
                .isEqualByComparingTo("80.00");

        mvc.perform(get("/api/v1/ops/reconciliation/mismatches").header("Authorization", ApiClient.bearer(RECONCILER)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ops/reconciliation/mismatches").header("Authorization", ApiClient.bearer(alice)))
                .andExpect(status().isForbidden());

        // Controlled correction happens elsewhere (here: undo the bad change); the next run closes the finding.
        jdbc.update("update account.account_balance set available = available - 10 where account_id = ?", payer);
        mvc.perform(post("/api/v1/ops/reconciliation/runs").header("Authorization", ApiClient.bearer(RECONCILER)))
                .andExpect(status().isOk());
        assertThat(openFor(payer)).isEmpty();
    }

    @Test
    void reservedAmountWithoutAReservationIsCritical() {
        jdbc.update("update account.account_balance set reserved = reserved + 5, available = available - 5 "
                + "where account_id = ?", payer);
        reconciliation.run();
        assertThat(openFor(payer)).extracting(m -> m.get("check_name")).contains("RESERVED_MATCHES_RESERVATIONS");
        jdbc.update("update account.account_balance set reserved = reserved - 5, available = available + 5 "
                + "where account_id = ?", payer);
    }
}
