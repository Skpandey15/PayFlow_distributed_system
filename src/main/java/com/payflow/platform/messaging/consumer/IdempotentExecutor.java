package com.payflow.platform.messaging.consumer;

import com.payflow.platform.messaging.inbox.InboxStore;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs a consumer's business effect exactly once per (consumer, eventId), as one local transaction:
 * <pre>
 *   BEGIN; claim eventId in inbox; apply change (+ outbox rows); COMMIT
 * </pre>
 * The use case's own {@code TransactionRunner} call joins this transaction (propagation REQUIRED), so the
 * claim, the business change and any outgoing events are atomic.
 */
@Component
public class IdempotentExecutor {

    private final TransactionRunner tx;
    private final InboxStore inbox;
    private final List<EventProcessingInterceptor> interceptors;
    private final Clock clock;

    public IdempotentExecutor(TransactionRunner tx, InboxStore inbox, List<EventProcessingInterceptor> interceptors,
                              Clock clock) {
        this.tx = tx;
        this.inbox = inbox;
        this.interceptors = interceptors;
        this.clock = clock;
    }

    public ConsumerOutcome once(String inboxTable, IncomingEvent event, Supplier<ConsumerOutcome> work) {
        return tx.inTransaction(() -> {
            if (!inbox.tryClaim(inboxTable, event.consumer(), event.eventId(), clock.instant())) {
                return ConsumerOutcome.DUPLICATE;
            }
            ConsumerOutcome outcome = work.get();
            interceptors.forEach(i -> i.beforeCommit(event));
            return outcome;
        });
    }
}
