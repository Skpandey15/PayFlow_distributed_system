package com.payflow.ledger.adapter.in.messaging;

import com.payflow.contracts.Topics;
import com.payflow.contracts.funds.FundsMessages.FundsCapturedV1;
import com.payflow.contracts.funds.FundsMessages.FundsDepositedV1;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase.PostJournalEntryCommand;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase.PostingLine;
import com.payflow.ledger.domain.LedgerAccounts;
import com.payflow.platform.messaging.consumer.ConsumerOutcome;
import com.payflow.platform.messaging.consumer.EventConsumerSupport;
import com.payflow.platform.messaging.consumer.IdempotentExecutor;
import com.payflow.platform.messaging.consumer.IncomingEvent;
import com.payflow.platform.messaging.dlq.DeadLetterObserver;
import com.payflow.platform.messaging.error.PermanentEventProcessingException;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ledger as an <b>event-driven follower</b> (choreography, not a saga step): it records the accounting effect of
 * every funds movement announced on {@code funds.events}. Group {@value #CONSUMER}.
 *
 * <p>This is what closes WP-01 finding M1 for the ledger. The posting is no longer a second synchronous write
 * after the payment commit. It is driven by an event that was committed atomically with the balance change,
 * and is therefore guaranteed to arrive (at least once). Two layers of idempotency apply: the inbox (eventId),
 * and the unique journal reference for the same fact re-announced under a new eventId.
 */
@Component
class LedgerFundsListener {

    static final String CONSUMER = "ledger-service";
    static final String INBOX = "ledger.processed_event";

    private final EventConsumerSupport consumer;
    private final IdempotentExecutor idempotent;
    private final PostJournalEntryUseCase ledger;
    private final DeadLetterObserver deadLetters;

    LedgerFundsListener(EventConsumerSupport consumer, IdempotentExecutor idempotent, PostJournalEntryUseCase ledger,
                        DeadLetterObserver deadLetters) {
        this.consumer = consumer;
        this.idempotent = idempotent;
        this.ledger = ledger;
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
    @KafkaListener(id = "ledger-funds", topics = Topics.FUNDS_EVENTS, groupId = CONSUMER)
    void onMessage(ConsumerRecord<String, String> record) {
        consumer.process(record, CONSUMER, this::handle);
    }

    private ConsumerOutcome handle(IncomingEvent event) {
        return switch (event.eventType()) {
            case "FundsCaptured" -> idempotent.once(INBOX, event, () -> {
                FundsCapturedV1 e = event.payload(FundsCapturedV1.class);
                Money amount = Money.of(e.amount(), e.currency());
                ledger.post(new PostJournalEntryCommand("payment:" + e.paymentId() + ":settlement",
                        "Settlement of payment " + e.paymentId(), List.of(
                        new PostingLine(AccountId.of(e.payerAccountId()), PostingLine.Side.DEBIT, amount),
                        new PostingLine(AccountId.of(e.payeeAccountId()), PostingLine.Side.CREDIT, amount))));
                return ConsumerOutcome.PROCESSED;
            });
            case "FundsDeposited" -> idempotent.once(INBOX, event, () -> {
                FundsDepositedV1 e = event.payload(FundsDepositedV1.class);
                Money amount = Money.of(e.amount(), e.currency());
                ledger.post(new PostJournalEntryCommand("deposit:" + e.depositId(), "Deposit " + e.depositId(), List.of(
                        new PostingLine(LedgerAccounts.EXTERNAL_FUNDING_CLEARING, PostingLine.Side.DEBIT, amount),
                        new PostingLine(AccountId.of(e.accountId()), PostingLine.Side.CREDIT, amount))));
                return ConsumerOutcome.PROCESSED;
            });
            // Reservations and releases are holds, not ledger movements.
            default -> ConsumerOutcome.IGNORED;
        };
    }

    @DltHandler
    void onDeadLetter(ConsumerRecord<String, String> record) {
        deadLetters.observe(record, CONSUMER);
    }
}
