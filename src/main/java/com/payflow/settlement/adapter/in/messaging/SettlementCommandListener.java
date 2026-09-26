package com.payflow.settlement.adapter.in.messaging;

import com.payflow.contracts.Topics;
import com.payflow.contracts.settlement.SettlementMessages.SubmitSettlementV1;
import com.payflow.platform.messaging.consumer.ConsumerOutcome;
import com.payflow.platform.messaging.consumer.EventConsumerSupport;
import com.payflow.platform.messaging.consumer.IncomingEvent;
import com.payflow.platform.messaging.dlq.DeadLetterObserver;
import com.payflow.platform.messaging.error.InvalidEventException;
import com.payflow.platform.messaging.error.PermanentEventProcessingException;
import com.payflow.settlement.application.port.in.SubmitSettlementUseCase;
import com.payflow.settlement.application.port.in.SubmitSettlementUseCase.Method;
import com.payflow.settlement.application.port.in.SubmitSettlementUseCase.SubmitSettlementCommand;
import com.payflow.shared.domain.Identifiers;
import com.payflow.shared.domain.Money;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: {@code settlement.commands} → {@link SubmitSettlementUseCase}. Group {@value #CONSUMER}.
 *
 * <p>No inbox here, deliberately. Handling spans a network call to the rail, so it cannot be one local
 * transaction with an eventId claim. Idempotency is natural instead: one settlement per payment (unique), a
 * resumable PENDING state, and the provider idempotency key = paymentId. A rail outage is transient: bounded
 * retries, then the DLT, then saga recovery re-issues the command and it resumes.
 */
@Component
class SettlementCommandListener {

    static final String CONSUMER = "settlement-service";

    private final EventConsumerSupport consumer;
    private final SubmitSettlementUseCase settlement;
    private final DeadLetterObserver deadLetters;

    SettlementCommandListener(EventConsumerSupport consumer, SubmitSettlementUseCase settlement,
                              DeadLetterObserver deadLetters) {
        this.consumer = consumer;
        this.settlement = settlement;
        this.deadLetters = deadLetters;
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
    @KafkaListener(id = "settlement-commands", topics = Topics.SETTLEMENT_COMMANDS, groupId = CONSUMER)
    void onMessage(ConsumerRecord<String, String> record) {
        consumer.process(record, CONSUMER, this::handle);
    }

    private ConsumerOutcome handle(IncomingEvent event) {
        if (!"SubmitSettlement".equals(event.eventType())) {
            throw new InvalidEventException("UNEXPECTED_EVENT_TYPE", event.eventType() + " is not handled by " + CONSUMER, null);
        }
        SubmitSettlementV1 c = event.payload(SubmitSettlementV1.class);
        Method method;
        try {
            method = Method.valueOf(c.method());
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("UNKNOWN_SETTLEMENT_METHOD", "Unknown settlement method", e);
        }
        settlement.submit(new SubmitSettlementCommand(Identifiers.parse(c.paymentId(), "payment id"), method,
                Money.of(c.amount(), c.currency()), c.reference()));
        return ConsumerOutcome.PROCESSED;
    }

    @DltHandler
    void onDeadLetter(ConsumerRecord<String, String> record) {
        deadLetters.observe(record, CONSUMER);
    }
}
