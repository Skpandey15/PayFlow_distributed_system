package com.payflow.settlement.domain;

import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

public record SettlementSnapshot(
        UUID id,
        UUID paymentId,
        SettlementRail rail,
        Money amount,
        String paymentReference,
        SettlementStatus status,
        String providerReference,
        String declineReason,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
