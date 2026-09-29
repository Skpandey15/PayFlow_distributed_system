package com.payflow.settlement.application.port.out;

import com.payflow.settlement.domain.Settlement;

/** Announces terminal settlement outcomes. Implementations write in the caller's transaction (outbox). */
public interface SettlementEventPublisherPort {

    void announceOutcome(Settlement settlement);
}
