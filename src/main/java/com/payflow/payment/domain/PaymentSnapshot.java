package com.payflow.payment.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.time.Instant;

/**
 * Complete, immutable state of a {@link Payment}. Used by persistence mappers to rehydrate and
 * externalise the aggregate without the domain knowing about JPA, and without exposing setters.
 *
 * @param version optimistic-concurrency token as read from storage; the domain never changes it
 */
public record PaymentSnapshot(
        PaymentId id,
        AccountId payerAccountId,
        AccountId payeeAccountId,
        Money amount,
        PaymentMethod method,
        String reference,
        String initiatedBy,
        PaymentStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
