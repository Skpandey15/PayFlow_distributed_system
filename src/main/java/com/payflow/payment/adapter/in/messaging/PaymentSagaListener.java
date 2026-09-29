package com.payflow.payment.adapter.in.messaging;

import com.payflow.contracts.Topics;
import com.payflow.contracts.fraud.FraudMessages.RiskAssessedV2;
import com.payflow.contracts.funds.FundsMessages.FundsReservationFailedV1;
import com.payflow.contracts.settlement.SettlementMessages.SettlementDeclinedV1;
import com.payflow.payment.application.port.in.PaymentSagaUseCase;
import com.payflow.payment.application.port.in.PaymentSagaUseCase.SagaTransition;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.SagaStep;
import com.payflow.platform.messaging.MessageContext;
import com.payflow.platform.messaging.MessagingMetrics;
import com.payflow.platform.messaging.consumer.ConsumerOutcome;
import com.payflow.platform.messaging.consumer.EventConsumerSupport;
import com.payflow.platform.messaging.consumer.IdempotentExecutor;
import com.payflow.platform.messaging.consumer.IncomingEvent;
import com.payflow.platform.messaging.dlq.DeadLetterObserver;
import com.payflow.platform.messaging.error.InvalidEventException;
import com.payflow.platform.messaging.error.PermanentEventProcessingException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Inbound adapter for saga replies: {@code fraud.events}, {@code funds.events}, {@code settlement.events} →
 * {@link PaymentSagaUseCase}. Consumer group {@value #CONSUMER}.
 *
 * <p>This adapter is the saga's logging boundary. It logs each transition (or a stale reply) once, with sagaId
 * and step, and counts compensations and terminal outcomes, because the application layer stays free of logging
 * frameworks.
 */
@Component
class PaymentSagaListener {

    static final String CONSUMER = "payment-service";
    static final String INBOX = "payment.processed_event";
    private static final Set<String> NOT_FOR_SAGA = Set.of("FundsDeposited");
    private static final Logger log = LoggerFactory.getLogger("payflow.saga");

    private final EventConsumerSupport consumer;
    private final IdempotentExecutor idempotent;
    private final PaymentSagaUseCase saga;
    private final DeadLetterObserver deadLetters;
    private final MessagingMetrics metrics;
    private final Clock clock;

    PaymentSagaListener(EventConsumerSupport consumer, IdempotentExecutor idempotent, PaymentSagaUseCase saga,
                        DeadLetterObserver deadLetters, MessagingMetrics metrics, Clock clock) {
        this.consumer = consumer;
        this.idempotent = idempotent;
        this.saga = saga;
        this.deadLetters = deadLetters;
        this.metrics = metrics;
        this.clock = clock;
    }

    @RetryableTopic(
            attempts = "${payflow.messaging.retry.attempts:4}",
            backOff = @BackOff(delayString = "${payflow.messaging.retry.initial-delay-ms:1000}",
                    multiplierString = "${payflow.messaging.retry.multiplier:3}",
                    maxDelayString = "${payflow.messaging.retry.max-delay-ms:30000}"),
            exclude = PermanentEventProcessingException.class,
            traversingCauses = "true",
            retryTopicSuffix = "-" + CONSUMER + "-retry",
            dltTopicSuffix = "-" + CONSUMER + "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            numPartitions = "${payflow.messaging.topic.partitions:6}",
            replicationFactor = "${payflow.messaging.topic.replication-factor:1}")
    @KafkaListener(id = "payment-saga", groupId = CONSUMER,
            topics = {Topics.FRAUD_EVENTS, Topics.FUNDS_EVENTS, Topics.SETTLEMENT_EVENTS})
    void onMessage(ConsumerRecord<String, String> record) {
        consumer.process(record, CONSUMER, event -> NOT_FOR_SAGA.contains(event.eventType())
                ? ConsumerOutcome.IGNORED
                : idempotent.once(INBOX, event, () -> dispatch(event)));
    }

    private ConsumerOutcome dispatch(IncomingEvent event) {
        PaymentId paymentId = PaymentId.of(event.envelope().aggregateId());
        SagaTransition transition = switch (event.eventType()) {
            case "RiskAssessed" -> {
                RiskAssessedV2 r = event.payload(RiskAssessedV2.class);
                yield saga.onRiskAssessed(paymentId, r.approved(), r.reason());
            }
            case "FundsReserved" -> saga.onFundsReserved(paymentId);
            case "FundsReservationFailed" ->
                    saga.onFundsReservationFailed(paymentId, event.payload(FundsReservationFailedV1.class).reason());
            case "FundsCaptured" -> saga.onFundsCaptured(paymentId);
            case "FundsReleased" -> saga.onFundsReleased(paymentId);
            case "SettlementCompleted" -> saga.onSettlementCompleted(paymentId);
            case "SettlementDeclined" ->
                    saga.onSettlementDeclined(paymentId, event.payload(SettlementDeclinedV1.class).reason());
            default -> throw new InvalidEventException("UNEXPECTED_EVENT_TYPE",
                    event.eventType() + " is not handled by " + CONSUMER, null);
        };
        record(transition);
        return transition.applied() ? ConsumerOutcome.PROCESSED : ConsumerOutcome.STALE;
    }

    private void record(SagaTransition t) {
        MessageContext.put(MessageContext.SAGA_ID, t.sagaId());
        if (!t.applied()) {
            log.atInfo().addKeyValue("sagaStep", t.from()).log("stale saga reply ignored");
            return;
        }
        metrics.count("payflow.saga.transitions", "from", t.from().name(), "to", t.to().name());
        Instant now = clock.instant();
        // Time the saga spent waiting in the step that just ended: which participant is slow?
        metrics.time("payflow.saga.step.duration", Duration.between(t.fromStepStartedAt(), now), "step", t.from().name());
        if (t.to().isTerminal()) {
            // Acceptance (HTTP 202) to terminal outcome: the payment-completion SLI, distinct from API latency.
            metrics.time("payflow.saga.completion", Duration.between(t.sagaStartedAt(), now), "outcome", t.to().name());
        }
        if (t.to() == SagaStep.COMPENSATING) {
            metrics.count("payflow.saga.compensations");
            log.atWarn().addKeyValue("sagaStepFrom", t.from()).addKeyValue("sagaStep", t.to())
                    .log("saga compensation started");
        } else {
            log.atInfo().addKeyValue("sagaStepFrom", t.from()).addKeyValue("sagaStep", t.to()).log("saga advanced");
        }
    }

    @DltHandler
    void onDeadLetter(ConsumerRecord<String, String> record) {
        deadLetters.observe(record, CONSUMER);
    }
}
