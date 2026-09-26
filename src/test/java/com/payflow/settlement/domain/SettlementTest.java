package com.payflow.settlement.domain;

import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementTest {

    final Instant now = Instant.parse("2026-09-01T10:00:00Z");

    Settlement pending() {
        return Settlement.initiate(UUID.randomUUID(), SettlementRail.CARD_NETWORK, Money.of("10", "USD"), "ref", now);
    }

    @Test
    void completesOnceWithProviderReference() {
        Settlement s = pending();
        s.complete("CARD-123", now);
        assertThat(s.status()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(s.providerReference()).isEqualTo("CARD-123");
        assertThatThrownBy(() -> s.complete("CARD-456", now)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> s.decline("late", now)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void completionWithoutProviderReferenceIsRejected() {
        assertThatThrownBy(() -> pending().complete(" ", now))
                .extracting("code").isEqualTo("SETTLEMENT_PROVIDER_REFERENCE_REQUIRED");
    }

    @Test
    void declineDefaultsItsReason() {
        Settlement s = pending();
        s.decline(null, now);
        assertThat(s.status().isTerminal()).isTrue();
        assertThat(s.declineReason()).isEqualTo("DECLINED_BY_RAIL");
    }
}
