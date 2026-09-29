package com.payflow.settlement.adapter.out.messaging;

import com.payflow.contracts.Producers;
import com.payflow.contracts.settlement.SettlementMessages.SettlementCompletedV1;
import com.payflow.contracts.settlement.SettlementMessages.SettlementDeclinedV1;
import com.payflow.platform.messaging.OutgoingMessage;
import com.payflow.platform.messaging.outbox.OutboxWriter;
import com.payflow.settlement.application.port.out.SettlementEventPublisherPort;
import com.payflow.settlement.domain.Settlement;
import com.payflow.settlement.domain.SettlementStatus;
import org.springframework.stereotype.Component;

@Component
class OutboxSettlementEventPublisher implements SettlementEventPublisherPort {

    static final String OUTBOX = "settlement.outbox_event";

    private final OutboxWriter outbox;

    OutboxSettlementEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void announceOutcome(Settlement s) {
        String paymentId = s.paymentId().toString();
        Object payload = s.status() == SettlementStatus.COMPLETED
                ? new SettlementCompletedV1(paymentId, s.id().toString(), s.providerReference())
                : new SettlementDeclinedV1(paymentId, s.id().toString(), s.declineReason());
        outbox.append(OUTBOX, Producers.SETTLEMENT, OutgoingMessage.of(payload, "Payment", paymentId));
    }
}
