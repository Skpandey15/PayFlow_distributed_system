package com.payflow.messaging;

import com.payflow.contracts.Producers;
import com.payflow.contracts.Topics;
import com.payflow.contracts.funds.FundsMessages.FundsDepositedV1;
import com.payflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.payflow.platform.messaging.EnvelopeFactory;
import com.payflow.platform.messaging.MessagingHeaders;
import com.payflow.platform.messaging.OutgoingMessage;
import com.payflow.platform.messaging.PreparedMessage;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.FaultInjection;
import com.payflow.support.IntegrationTest;
import com.payflow.support.KafkaTestSupport;
import com.payflow.support.TestJwt;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kafka consumer semantics under failure: at-least-once delivery, idempotent consumers, offsets, retry topics,
 * DLT, poison messages, schema-version rejection, untrusted producers, DLQ replay, and consumer restart/rebalance.
 *
 * <p>Probe: a {@code FundsDeposited} event on {@code funds.events}. The Ledger consumer (inbox-protected) turns it
 * into a journal entry, and its effect (the ledger balance) makes double processing directly observable.
 */
@IntegrationTest
class ConsumerSemanticsIT {

    static final String LEDGER = "ledger-service";
    static final String LEDGER_DLT = "funds.events-ledger-service-dlt";

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    ConsumerFactory<String, String> consumerFactory;
    @Autowired
    EnvelopeFactory envelopes;
    @Autowired
    GetAccountBalanceUseCase balances;
    @Autowired
    FaultInjection faults;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MeterRegistry meters;
    @Autowired
    KafkaListenerEndpointRegistry listeners;
    @Autowired
    MockMvc mvc;

    KafkaTestSupport kafka() {
        return new KafkaTestSupport(kafka, consumerFactory, envelopes);
    }

    PreparedMessage deposit(AccountId account, String amount) {
        return kafka().prepare(Producers.ACCOUNT, OutgoingMessage.of(
                new FundsDepositedV1(UUID.randomUUID().toString(), account.toString(), amount, "USD"),
                "Account", account.toString()));
    }

    Money ledger(AccountId account) {
        return balances.balance(Actors.ledgerReader(), account, "USD").balance();
    }

    int inboxRows(UUID eventId) {
        return jdbc.queryForObject("select count(*) from ledger.processed_event where event_id = ?", Integer.class, eventId);
    }

    double ledgerOutcomes(String outcome) {
        var c = meters.find("payflow.events.consumed").tags("consumer", "ledger-service", "outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    List<ConsumerRecord<String, String>> deadLetters(String topic, String key) {
        return kafka().readKey(topic, key);
    }

    /** Failure 3: duplicate delivery of the same event (same eventId). */
    @Test
    void duplicateEventHasExactlyOneEffect() {
        AccountId account = AccountId.newId();
        PreparedMessage m = deposit(account, "10.00");
        double duplicatesBefore = ledgerOutcomes("DUPLICATE");

        kafka().send(m.toRecord());
        kafka().send(m.toRecord());

        await().atMost(Duration.ofSeconds(15)).until(() -> ledgerOutcomes("DUPLICATE") > duplicatesBefore);
        assertThat(ledger(account)).isEqualTo(Money.of("10.00", "USD"));
        assertThat(inboxRows(m.envelope().eventId())).isEqualTo(1);
    }

    /** Failure 4: consumer crashes before the DB commit. The transaction rolls back, redelivery applies it once. */
    @Test
    void crashBeforeCommitIsRolledBackAndRetried() {
        AccountId account = AccountId.newId();
        PreparedMessage m = deposit(account, "20.00");
        faults.arm(LEDGER, m.envelope().eventId(), FaultInjection.Point.BEFORE_COMMIT, 1);

        kafka().send(m.toRecord());

        await().atMost(Duration.ofSeconds(15)).until(() -> ledger(account).equals(Money.of("20.00", "USD")));
        assertThat(inboxRows(m.envelope().eventId())).isEqualTo(1);
    }

    /** Failure 5: consumer crashes after the DB commit and before the offset commit. Redelivered, deduplicated by the inbox. */
    @Test
    void crashAfterCommitBeforeOffsetCommitIsRedeliveredAndDeduplicated() {
        AccountId account = AccountId.newId();
        PreparedMessage m = deposit(account, "30.00");
        double duplicatesBefore = ledgerOutcomes("DUPLICATE");
        faults.arm(LEDGER, m.envelope().eventId(), FaultInjection.Point.AFTER_COMMIT, 1);

        kafka().send(m.toRecord());

        await().atMost(Duration.ofSeconds(15)).until(() -> ledgerOutcomes("DUPLICATE") > duplicatesBefore);
        assertThat(ledger(account)).as("redelivered but applied once").isEqualTo(Money.of("30.00", "USD"));
    }

    /** Failure 12: a poison message is quarantined without retries and does not block its partition. */
    @Test
    void poisonMessageIsDeadLetteredAndDoesNotBlockThePartition() {
        AccountId account = AccountId.newId();
        String key = account.toString();
        kafka().sendRaw(Topics.FUNDS_EVENTS, key, "{this is not json");
        PreparedMessage next = deposit(account, "5.00");
        kafka().send(next.toRecord()); // same key → same partition, right behind the poison message

        await().atMost(Duration.ofSeconds(15)).until(() -> ledger(account).equals(Money.of("5.00", "USD")));
        await().atMost(Duration.ofSeconds(15)).until(() -> !deadLetters(LEDGER_DLT, key).isEmpty());
        ConsumerRecord<String, String> dead = deadLetters(LEDGER_DLT, key).getFirst();
        assertThat(KafkaTestSupport.header(dead, MessagingHeaders.FAILURE_CATEGORY)).isEqualTo("DESERIALIZATION");
        assertThat(KafkaTestSupport.header(dead, MessagingHeaders.ERROR_CODE)).isEqualTo("EVENT_NOT_DESERIALIZABLE");
        assertThat(KafkaTestSupport.header(dead, KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).as("no stack traces in events").isNull();
        assertThat(KafkaTestSupport.header(dead, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNull();
        assertThat(KafkaTestSupport.header(dead, KafkaHeaders.ORIGINAL_TOPIC)).isEqualTo(Topics.FUNDS_EVENTS);
        assertThat(java.nio.ByteBuffer.wrap(dead.headers().lastHeader("retry_topic-attempts").value()).getInt())
                .as("header = attempts made + 1: permanent → dead-lettered after its first attempt, never retried")
                .isEqualTo(2);
    }

    /** Failure 13: an unsupported schema version goes straight to the DLT. */
    @Test
    void unsupportedVersionIsDeadLetteredWithoutRetry() {
        AccountId account = AccountId.newId();
        PreparedMessage m = deposit(account, "1.00");
        String v99 = m.json().replace("\"eventVersion\":1", "\"eventVersion\":99");
        kafka().sendRaw(Topics.FUNDS_EVENTS, account.toString(), v99);

        await().atMost(Duration.ofSeconds(15)).until(() -> !deadLetters(LEDGER_DLT, account.toString()).isEmpty());
        ConsumerRecord<String, String> dead = deadLetters(LEDGER_DLT, account.toString()).getFirst();
        assertThat(KafkaTestSupport.header(dead, MessagingHeaders.FAILURE_CATEGORY)).isEqualTo("UNSUPPORTED_VERSION");
        assertThat(ledger(account).isZero()).isTrue();
    }

    /** Zero Trust: a well-formed event from the wrong producer is rejected as untrusted, not processed. */
    @Test
    void eventFromUnauthorizedProducerIsRejected() {
        AccountId account = AccountId.newId();
        String forged = deposit(account, "1000000.00").json()
                .replace("\"producer\":\"account-service\"", "\"producer\":\"payment-service\"");
        kafka().sendRaw(Topics.FUNDS_EVENTS, account.toString(), forged);

        await().atMost(Duration.ofSeconds(15)).until(() -> !deadLetters(LEDGER_DLT, account.toString()).isEmpty());
        assertThat(KafkaTestSupport.header(deadLetters(LEDGER_DLT, account.toString()).getFirst(),
                MessagingHeaders.FAILURE_CATEGORY)).isEqualTo("UNTRUSTED_SOURCE");
        assertThat(ledger(account).isZero()).as("forged money never reaches the ledger").isTrue();
    }

    /** Failures 14 + 15: bounded retries end in the DLT; a controlled replay applies it once, and a second replay is a no-op. */
    @Test
    void retryExhaustionDeadLettersAndReplayAppliesExactlyOnce() throws Exception {
        AccountId account = AccountId.newId();
        PreparedMessage m = deposit(account, "40.00");
        faults.arm(LEDGER, m.envelope().eventId(), FaultInjection.Point.BEFORE_COMMIT, Integer.MAX_VALUE);

        kafka().send(m.toRecord());

        await().atMost(Duration.ofSeconds(20)).until(() -> !deadLetters(LEDGER_DLT, account.toString()).isEmpty());
        ConsumerRecord<String, String> dead = deadLetters(LEDGER_DLT, account.toString()).getFirst();
        assertThat(KafkaTestSupport.header(dead, MessagingHeaders.FAILURE_CATEGORY)).isEqualTo("TRANSIENT_INFRASTRUCTURE");
        assertThat(java.nio.ByteBuffer.wrap(dead.headers().lastHeader("retry_topic-attempts").value()).getInt())
                .as("header = attempts made + 1: 1 original + 2 bounded retries (test profile attempts=3)")
                .isEqualTo(4);
        assertThat(KafkaTestSupport.header(dead, MessagingHeaders.EVENT_ID)).isEqualTo(m.envelope().eventId().toString());
        assertThat(ledger(account).isZero()).as("nothing applied while failing").isTrue();

        faults.disarm(LEDGER, m.envelope().eventId()); // the "fix" is deployed
        String ops = TestJwt.token("ops-1", "ops:dlq-replay");
        String body = "{\"dltTopic\":\"" + LEDGER_DLT + "\",\"partition\":" + dead.partition() + ",\"offset\":" + dead.offset() + "}";
        mvc.perform(post("/api/v1/ops/dead-letters/replay").header("Authorization", ApiClient.bearer(ops))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayedTo").value("funds.events-ledger-service-retry-0"))
                .andExpect(jsonPath("$.eventId").value(m.envelope().eventId().toString()));
        await().atMost(Duration.ofSeconds(15)).until(() -> ledger(account).equals(Money.of("40.00", "USD")));

        double duplicatesBefore = ledgerOutcomes("DUPLICATE");
        mvc.perform(post("/api/v1/ops/dead-letters/replay").header("Authorization", ApiClient.bearer(ops))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(15)).until(() -> ledgerOutcomes("DUPLICATE") > duplicatesBefore);
        assertThat(ledger(account)).as("second replay is harmless").isEqualTo(Money.of("40.00", "USD"));
    }

    /** Failures 6, 8, 9: consumer stops (crash), events accumulate, it restarts (rebalance) and resumes from committed offsets. */
    @Test
    void stoppedConsumerResumesFromCommittedOffsetsWithoutLossOrDuplication() {
        MessageListenerContainer ledgerConsumer = listeners.getListenerContainer("ledger-funds");
        AccountId account = AccountId.newId();
        ledgerConsumer.stop();
        try {
            for (int i = 0; i < 10; i++) {
                kafka().send(deposit(account, "1.00").toRecord());
            }
            assertThat(ledger(account).isZero()).as("nobody is consuming").isTrue();
        } finally {
            ledgerConsumer.start(); // rejoins the group → partitions reassigned → resumes at committed offsets
        }
        await().atMost(Duration.ofSeconds(30)).until(() -> ledger(account).equals(Money.of("10.00", "USD")));
    }
}
