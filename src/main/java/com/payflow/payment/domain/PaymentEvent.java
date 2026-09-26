package com.payflow.payment.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.DomainEvent;
import com.payflow.shared.domain.Money;

import java.time.Instant;

/**
 * Domain events recorded by the {@link Payment} aggregate. Sealed, so every consumer's
 * {@code switch} is exhaustiveness-checked by the compiler when a new event is added.
 * These are the future integration events (WP-02: Outbox -> Kafka -> Fraud/Ledger/Settlement).
 */
public sealed interface PaymentEvent extends DomainEvent {

    PaymentId paymentId();

    record PaymentCreated(PaymentId paymentId, AccountId payerAccountId, AccountId payeeAccountId,
                          Money amount, PaymentMethod method, Instant occurredAt) implements PaymentEvent {
    }

    record PaymentAuthorized(PaymentId paymentId, Instant occurredAt) implements PaymentEvent {
    }

    record PaymentRejected(PaymentId paymentId, String reason, Instant occurredAt) implements PaymentEvent {
    }

    record PaymentCancelled(PaymentId paymentId, Instant occurredAt) implements PaymentEvent {
    }

    record PaymentProcessingStarted(PaymentId paymentId, Instant occurredAt) implements PaymentEvent {
    }

    record PaymentSettled(PaymentId paymentId, Money amount, Instant occurredAt) implements PaymentEvent {
    }

    record PaymentFailed(PaymentId paymentId, String reason, Instant occurredAt) implements PaymentEvent {
    }
}
