package com.payflow.payment.adapter.out.messaging;

import com.payflow.contracts.Producers;
import com.payflow.contracts.payment.PaymentEvents.PaymentAuthorizedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentCancelledV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentCreatedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentFailedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentProcessingStartedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentRejectedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentSettledV1;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.domain.PaymentEvent;
import com.payflow.platform.messaging.OutgoingMessage;
import com.payflow.platform.messaging.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates internal domain events into the published {@code payment.events} contract (Anti-Corruption:
 * internal events may be refactored freely; the contract changes only through versioning) and writes them
 * to the Payment outbox in the current transaction. Replaces WP-01's logging publisher.
 */
@Component
class OutboxPaymentEventPublisher implements PaymentEventPublisherPort {

    private final OutboxWriter outbox;

    OutboxPaymentEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(List<PaymentEvent> events) {
        for (PaymentEvent event : events) {
            String id = event.paymentId().toString();
            outbox.append(OutboxSagaCommandPublisher.OUTBOX, Producers.PAYMENT,
                    OutgoingMessage.of(toContract(event), "Payment", id));
        }
    }

    static Object toContract(PaymentEvent event) {
        String id = event.paymentId().toString();
        return switch (event) {
            case PaymentEvent.PaymentCreated e -> new PaymentCreatedV1(id, e.payerAccountId().toString(),
                    e.payeeAccountId().toString(), e.amount().amount().toPlainString(), e.amount().currencyCode(),
                    e.method().name());
            case PaymentEvent.PaymentAuthorized e -> new PaymentAuthorizedV1(id);
            case PaymentEvent.PaymentProcessingStarted e -> new PaymentProcessingStartedV1(id);
            case PaymentEvent.PaymentRejected e -> new PaymentRejectedV1(id, e.reason());
            case PaymentEvent.PaymentCancelled e -> new PaymentCancelledV1(id);
            case PaymentEvent.PaymentSettled e -> new PaymentSettledV1(id, e.amount().amount().toPlainString(),
                    e.amount().currencyCode());
            case PaymentEvent.PaymentFailed e -> new PaymentFailedV1(id, e.reason());
        };
    }
}
