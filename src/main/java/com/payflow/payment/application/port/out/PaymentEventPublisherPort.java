package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.PaymentEvent;

import java.util.List;

/**
 * Publishes payment lifecycle events to {@code payment.events}. Use cases call it <em>inside</em> the
 * transaction that changed the aggregate, and the WP-02 implementation writes to the Payment outbox in that
 * same transaction. State change and announcement are therefore atomic (WP-01 finding M1 closed).
 */
public interface PaymentEventPublisherPort {

    void publish(List<PaymentEvent> events);
}
