package com.payflow.fraud.adapter.in.messaging;

import com.payflow.contracts.Topics;
import com.payflow.contracts.fraud.FraudMessages.AssessPaymentRiskV1;
import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase;
import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase.AssessRiskCommand;
import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase.Channel;
import com.payflow.platform.messaging.consumer.ConsumerOutcome;
import com.payflow.platform.messaging.consumer.EventConsumerSupport;
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

/**
 * Inbound adapter: {@code fraud.commands} → {@link AssessPaymentRiskUseCase}. Group {@value #CONSUMER}.
 * Idempotency is natural: the MongoDB unique index gives one assessment per payment, and a re-assessment returns
 * and re-publishes the stored decision. MongoDB outages are transient: bounded retries, then the DLT, then saga
 * recovery re-issues the command.
 */
@Component
class FraudCommandListener {

    static final String CONSUMER = "fraud-service";

    private final EventConsumerSupport consumer;
    private final AssessPaymentRiskUseCase fraud;
    private final DeadLetterObserver deadLetters;

    FraudCommandListener(EventConsumerSupport consumer, AssessPaymentRiskUseCase fraud, DeadLetterObserver deadLetters) {
        this.consumer = consumer;
        this.fraud = fraud;
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
    @KafkaListener(id = "fraud-commands", topics = Topics.FRAUD_COMMANDS, groupId = CONSUMER)
    void onMessage(ConsumerRecord<String, String> record) {
        consumer.process(record, CONSUMER, this::handle);
    }

    private ConsumerOutcome handle(IncomingEvent event) {
        if (!"AssessPaymentRisk".equals(event.eventType())) {
            throw new InvalidEventException("UNEXPECTED_EVENT_TYPE", event.eventType() + " is not handled by " + CONSUMER, null);
        }
        AssessPaymentRiskV1 c = event.payload(AssessPaymentRiskV1.class);
        fraud.assess(new AssessRiskCommand(Identifiers.parse(c.paymentId(), "payment id"),
                AccountId.of(c.payerAccountId()), AccountId.of(c.payeeAccountId()), Money.of(c.amount(), c.currency()),
                c.method(), new Channel(c.deviceId(), c.ipAddress(), c.userAgent(), c.countryCode())));
        return ConsumerOutcome.PROCESSED;
    }

    @DltHandler
    void onDeadLetter(ConsumerRecord<String, String> record) {
        deadLetters.observe(record, CONSUMER);
    }
}
