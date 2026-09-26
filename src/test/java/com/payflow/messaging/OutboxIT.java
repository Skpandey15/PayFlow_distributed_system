package com.payflow.messaging;

import com.payflow.contracts.Topics;
import com.payflow.platform.messaging.MessagingHeaders;
import com.payflow.platform.messaging.outbox.OutboxRelay;
import com.payflow.platform.messaging.outbox.OutboxRelayFactory;
import com.payflow.platform.messaging.outbox.OutboxRelayScheduler;
import com.payflow.support.Actors;
import com.payflow.support.ApiClient;
import com.payflow.support.IntegrationTest;
import com.payflow.support.KafkaTestSupport;
import com.payflow.support.TestJwt;
import com.payflow.platform.messaging.EnvelopeFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Transactional Outbox failure engineering (WP-01 finding M1). Each test reproduces one crash window of the
 * database/broker dual write and proves that no event is lost and that duplicates are harmless.
 */
@IntegrationTest
class OutboxIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OutboxRelayScheduler relays;
    @Autowired
    OutboxRelayFactory relayFactory;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    ConsumerFactory<String, String> consumerFactory;
    @Autowired
    EnvelopeFactory envelopes;

    ApiClient api;
    KafkaTestSupport kafkaSupport;
    String alice;
    UUID payer;
    UUID payee;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        kafkaSupport = new KafkaTestSupport(kafka, consumerFactory, envelopes);
        alice = TestJwt.token("alice-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES);
        payer = api.fundedAccount(alice, "EUR", "100.00");
        payee = api.openAccount(TestJwt.token("bob-" + UUID.randomUUID(), Actors.CUSTOMER_SCOPES), "EUR");
    }

    @AfterEach
    void resumeRelay() {
        relays.resume();
    }

    @Test
    void stateChangeAndEventCommitAtomically() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee,
                "10.00", "EUR", "CARD", null));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select event_type, topic, message_key, correlation_id from payment.outbox_event where aggregate_id = ? order by id",
                paymentId.toString());
        assertThat(rows).extracting(r -> r.get("event_type")).startsWith("PaymentCreated", "AssessPaymentRisk");
        assertThat(rows).allSatisfy(r -> assertThat(r.get("message_key")).isEqualTo(paymentId.toString()));
    }

    /** Failure 1: the process "crashes" after the DB commit and before publication. Nothing is lost. */
    @Test
    void eventsCommittedWhilePublisherIsDownArePublishedAfterRestart() throws Exception {
        relays.pause(); // equivalent to the process dying right after COMMIT: rows exist, nothing sent
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee,
                "10.00", "EUR", "CARD", null));
        Integer pending = jdbc.queryForObject(
                "select count(*) from payment.outbox_event where aggregate_id = ? and published_at is null",
                Integer.class, paymentId.toString());
        assertThat(pending).isEqualTo(2);
        assertThat(kafkaSupport.readKey(Topics.PAYMENT_EVENTS, paymentId.toString())).isEmpty();

        relays.resume(); // "restart"

        api.awaitStatus(alice, paymentId, "SETTLED");
        assertThat(api.accountField(payer, "$.availableBalance")).isEqualTo("90.00");
    }

    /** Failure 2: the publisher crashes after the broker ack but before marking the row. A duplicate is published and absorbed. */
    @Test
    void republishedEventIsDeduplicatedByConsumers() throws Exception {
        relays.pause();
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee,
                "10.00", "EUR", "CARD", null));
        // Simulate "sent, then crashed before UPDATE published_at": publish the rows by hand, leave them unmarked.
        for (Map<String, Object> row : jdbc.queryForList(
                "select topic, message_key, envelope::text as envelope, event_id::text as event_id, event_type, event_version,"
                        + " correlation_id from payment.outbox_event where aggregate_id = ? order by id", paymentId.toString())) {
            ProducerRecord<String, String> record = new ProducerRecord<>((String) row.get("topic"),
                    (String) row.get("message_key"), (String) row.get("envelope"));
            record.headers().add(MessagingHeaders.EVENT_ID, ((String) row.get("event_id")).getBytes(StandardCharsets.UTF_8));
            kafkaSupport.send(record);
        }
        relays.resume(); // the relay now publishes the same rows again

        api.awaitStatus(alice, paymentId, "SETTLED");
        List<ConsumerRecord<String, String>> riskCommands = kafkaSupport.readKey(Topics.FRAUD_COMMANDS, paymentId.toString());
        assertThat(riskCommands).as("the command was on the log twice").hasSize(2);
        assertThat(riskCommands.stream().map(r -> KafkaTestSupport.header(r, MessagingHeaders.EVENT_ID)).distinct())
                .as("with the same eventId").hasSize(1);
        assertThat(api.accountField(payer, "$.availableBalance")).as("charged exactly once").isEqualTo("90.00");
        assertThat(jdbc.queryForObject("select count(*) from account.funds_reservation where payment_id = ?",
                Integer.class, paymentId)).isEqualTo(1);
    }

    /** Failure 7: broker unavailable. The API keeps accepting payments, events wait in the outbox, and nothing is lost. */
    @Test
    void unreachableBrokerLeavesEventsSafelyInTheOutbox() throws Exception {
        relays.pause();
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee,
                "10.00", "EUR", "CARD", null));

        KafkaTemplate<String, String> deadBroker = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500)));
        OutboxRelay relayWithDeadBroker = relayFactory.forTable("payment.outbox_event", deadBroker);

        // Other sagas may still be writing rows; the relay always attempts the OLDEST unpublished row first.
        Long oldest = jdbc.queryForObject("select min(id) from payment.outbox_event where published_at is null", Long.class);
        assertThat(relayWithDeadBroker.publishBatch()).isZero();
        Map<String, Object> head = jdbc.queryForMap(
                "select published_at, publish_attempts, last_error from payment.outbox_event where id = ?", oldest);
        assertThat(head.get("published_at")).isNull();
        assertThat((Integer) head.get("publish_attempts")).isGreaterThanOrEqualTo(1);
        assertThat((String) head.get("last_error")).isEqualTo("KafkaException");
        assertThat(jdbc.queryForObject("select count(*) from payment.outbox_event where aggregate_id = ?"
                + " and published_at is null", Integer.class, paymentId.toString()))
                .as("this payment's events are still safely in the outbox").isEqualTo(2);
        assertThat(relayWithDeadBroker.backlog()).isGreaterThanOrEqualTo(2);

        relays.resume(); // broker "back"
        api.awaitStatus(alice, paymentId, "SETTLED");
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "select count(*) from payment.outbox_event where aggregate_id = ? and published_at is null",
                Integer.class, paymentId.toString()) == 0);
    }

    /** Ordering: key = paymentId, so one payment's lifecycle is on one partition, in causal order. */
    @Test
    void lifecycleEventsOfOnePaymentAreOrderedOnASinglePartition() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee,
                "10.00", "EUR", "CARD", null));
        api.awaitStatus(alice, paymentId, "SETTLED");
        // The SETTLED commit and its PaymentSettled publication are decoupled by the outbox relay.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> kafkaSupport.readKey(Topics.PAYMENT_EVENTS, paymentId.toString()).size() == 4);

        List<ConsumerRecord<String, String>> lifecycle = kafkaSupport.readKey(Topics.PAYMENT_EVENTS, paymentId.toString());
        assertThat(lifecycle).extracting(ConsumerRecord::partition).as("single partition").containsOnly(
                lifecycle.getFirst().partition());
        assertThat(lifecycle).extracting(r -> KafkaTestSupport.header(r, MessagingHeaders.EVENT_TYPE))
                .containsExactly("PaymentCreated", "PaymentAuthorized", "PaymentProcessingStarted", "PaymentSettled");
        assertThat(lifecycle).extracting(ConsumerRecord::offset).isSorted();
    }

    @Test
    void relayPublishesTraceAndCorrelationContextFromTheOriginalRequest() throws Exception {
        UUID paymentId = api.createdPaymentId(api.createPayment(alice, UUID.randomUUID().toString(), payer, payee,
                "10.00", "EUR", "CARD", null));
        api.awaitStatus(alice, paymentId, "SETTLED");

        List<ConsumerRecord<String, String>> lifecycle = kafkaSupport.readKey(Topics.PAYMENT_EVENTS, paymentId.toString());
        String correlation = KafkaTestSupport.header(lifecycle.getFirst(), MessagingHeaders.CORRELATION_ID);
        assertThat(correlation).isNotBlank();
        assertThat(lifecycle).allSatisfy(r -> assertThat(KafkaTestSupport.header(r, MessagingHeaders.CORRELATION_ID))
                .as("one business correlation id across every hop").isEqualTo(correlation));
        String traceparent = KafkaTestSupport.header(lifecycle.getFirst(), MessagingHeaders.TRACEPARENT);
        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
    }
}
