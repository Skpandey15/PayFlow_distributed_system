package com.payflow.account.adapter.in.messaging;

import com.payflow.account.application.port.in.FundsCommandUseCase;
import com.payflow.account.application.port.in.FundsCommandUseCase.ReleaseFunds;
import com.payflow.account.application.port.in.FundsCommandUseCase.ReserveFunds;
import com.payflow.contracts.Topics;
import com.payflow.contracts.funds.FundsMessages.CaptureFundsV1;
import com.payflow.contracts.funds.FundsMessages.ReleaseFundsV1;
import com.payflow.contracts.funds.FundsMessages.ReserveFundsV1;
import com.payflow.platform.messaging.consumer.ConsumerOutcome;
import com.payflow.platform.messaging.consumer.EventConsumerSupport;
import com.payflow.platform.messaging.consumer.IdempotentExecutor;
import com.payflow.platform.messaging.consumer.IncomingEvent;
import com.payflow.platform.messaging.dlq.DeadLetterObserver;
import com.payflow.platform.messaging.error.InvalidEventException;
import com.payflow.platform.messaging.error.PermanentEventProcessingException;
import com.payflow.shared.domain.AccountId;
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

import java.util.UUID;

/**
 * Inbound adapter: {@code funds.commands} → {@link FundsCommandUseCase}. Group {@value #CONSUMER}.
 * Idempotency: inbox claim (eventId) in the same transaction as the funds change, plus the unique
 * reservation per payment for commands re-sent with a new eventId.
 */
@Component
class FundsCommandListener {

    static final String CONSUMER = "account-service";
    static final String INBOX = "account.processed_event";

    private final EventConsumerSupport consumer;
    private final IdempotentExecutor idempotent;
    private final FundsCommandUseCase funds;
    private final DeadLetterObserver deadLetters;

    FundsCommandListener(EventConsumerSupport consumer, IdempotentExecutor idempotent, FundsCommandUseCase funds,
                         DeadLetterObserver deadLetters) {
        this.consumer = consumer;
        this.idempotent = idempotent;
        this.funds = funds;
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
    @KafkaListener(id = "funds-commands", topics = Topics.FUNDS_COMMANDS, groupId = CONSUMER)
    void onMessage(ConsumerRecord<String, String> record) {
        consumer.process(record, CONSUMER, event -> idempotent.once(INBOX, event, () -> dispatch(event)));
    }

    private ConsumerOutcome dispatch(IncomingEvent event) {
        switch (event.eventType()) {
            case "ReserveFunds" -> {
                ReserveFundsV1 c = event.payload(ReserveFundsV1.class);
                funds.reserve(new ReserveFunds(uuid(c.paymentId()), AccountId.of(c.payerAccountId()),
                        AccountId.of(c.payeeAccountId()), Money.of(c.amount(), c.currency())));
            }
            case "CaptureFunds" -> funds.capture(uuid(event.payload(CaptureFundsV1.class).paymentId()));
            case "ReleaseFunds" -> {
                ReleaseFundsV1 c = event.payload(ReleaseFundsV1.class);
                funds.release(new ReleaseFunds(uuid(c.paymentId()), AccountId.of(c.payerAccountId()),
                        Money.of(c.amount(), c.currency()), c.reason()));
            }
            default -> throw new InvalidEventException("UNEXPECTED_EVENT_TYPE",
                    event.eventType() + " is not handled by " + CONSUMER, null);
        }
        return ConsumerOutcome.PROCESSED;
    }

    private static UUID uuid(String value) {
        return Identifiers.parse(value, "payment id");
    }

    @DltHandler
    void onDeadLetter(ConsumerRecord<String, String> record) {
        deadLetters.observe(record, CONSUMER);
    }
}
