package com.payflow.platform;

import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WP-03 edge overload control: per-subject rate limiting (429) and outbox-age admission control (503). */
@IntegrationTest
@TestPropertySource(properties = {
        "payflow.traffic.payments-per-subject-per-second=3",
        "payflow.traffic.admission-max-outbox-age=2s"})
class TrafficControlIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    KafkaContainer kafka;
    private boolean kafkaPaused;

    ApiClient api;
    UUID payee;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        payee = api.openAccount(TestJwt.token("payee-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES), "USD");
    }

    @AfterEach
    void unpauseKafka() {
        if (kafkaPaused) {
            DockerClientFactory.instance().client().unpauseContainerCmd(kafka.getContainerId()).exec();
            kafkaPaused = false;
        }
    }

    private int create(String token, UUID payer) throws Exception {
        return api.createPayment(token, UUID.randomUUID().toString(), payer, payee, "1.00", "USD", "CARD", null)
                .andReturn().getResponse().getStatus();
    }

    @Test
    void aSubjectAboveItsRateGets429WithRetryAfterWhileOthersAreUnaffected() throws Exception {
        String greedy = TestJwt.token("greedy-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        String polite = TestJwt.token("polite-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        UUID greedyAccount = api.fundedAccount(greedy, "USD", "100.00");
        UUID politeAccount = api.fundedAccount(polite, "USD", "100.00");

        int throttled = 0;
        for (int i = 0; i < 8; i++) {
            if (create(greedy, greedyAccount) == 429) {
                throttled++;
            }
        }
        assertThat(throttled).as("8 requests in well under a second against a 3/s limit").isGreaterThanOrEqualTo(4);
        api.createPayment(greedy, UUID.randomUUID().toString(), greedyAccount, payee, "1.00", "USD", "CARD", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        assertThat(create(polite, politeAccount)).as("limits are per subject").isEqualTo(201);
    }

    @Test
    void newPaymentsAreRefusedWhileTheOutboxBacklogIsTooOldAndAcceptedAgainAfterItDrains() throws Exception {
        String payer = TestJwt.token("payer-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        UUID account = api.fundedAccount(payer, "USD", "100.00");

        // The broker becomes unreachable (container really paused): events commit but cannot be published. Every
        // application context's relay is affected, exactly like a real outage.
        DockerClientFactory.instance().client().pauseContainerCmd(kafka.getContainerId()).exec();
        kafkaPaused = true;
        assertThat(create(payer, account)).as("a short outage is absorbed by the outbox").isEqualTo(201);
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(250))
                .until(() -> create(payer, account) == 503);
        api.createPayment(payer, UUID.randomUUID().toString(), account, payee, "1.00", "USD", "CARD", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("PAYMENTS_TEMPORARILY_UNAVAILABLE"));

        DockerClientFactory.instance().client().unpauseContainerCmd(kafka.getContainerId()).exec();
        kafkaPaused = false;
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250))
                .until(() -> create(payer, account) == 201);
    }
}
