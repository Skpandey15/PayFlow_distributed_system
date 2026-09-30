package com.payflow.payment.adapter.out.messaging;

import com.payflow.contracts.Producers;
import com.payflow.contracts.fraud.FraudMessages.AssessPaymentRiskV1;
import com.payflow.contracts.funds.FundsMessages.CaptureFundsV1;
import com.payflow.contracts.funds.FundsMessages.ReleaseFundsV1;
import com.payflow.contracts.funds.FundsMessages.ReserveFundsV1;
import com.payflow.contracts.settlement.SettlementMessages.SubmitSettlementV1;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.saga.CheckoutContext;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.platform.messaging.OutgoingMessage;
import com.payflow.platform.messaging.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

/**
 * Saga commands → Payment outbox → {@code fraud|funds|settlement.commands}. Every command is keyed by paymentId
 * (partition affinity: all commands for one payment are ordered) and carries the sagaId.
 * Commands are event-carried state: each one contains everything the participant needs, so participants
 * never call back into Payment.
 */
@Component
class OutboxSagaCommandPublisher implements SagaCommandPort {

    static final String OUTBOX = "payment.outbox_event";

    private final OutboxWriter outbox;

    OutboxSagaCommandPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void requestRiskAssessment(Payment p, PaymentSaga saga) {
        CheckoutContext c = saga.checkout();
        send(p, saga, new AssessPaymentRiskV1(p.id().toString(), p.payerAccountId().toString(),
                p.payeeAccountId().toString(), amount(p), p.amount().currencyCode(), p.method().name(),
                c.deviceId(), c.ipAddress(), c.userAgent(), c.countryCode()));
    }

    @Override
    public void reserveFunds(Payment p, PaymentSaga saga) {
        send(p, saga, new ReserveFundsV1(p.id().toString(), p.payerAccountId().toString(),
                p.payeeAccountId().toString(), amount(p), p.amount().currencyCode()));
    }

    @Override
    public void submitSettlement(Payment p, PaymentSaga saga) {
        send(p, saga, new SubmitSettlementV1(p.id().toString(), p.method().name(), amount(p),
                p.amount().currencyCode(), p.reference()));
    }

    @Override
    public void captureFunds(Payment p, PaymentSaga saga) {
        send(p, saga, new CaptureFundsV1(p.id().toString()));
    }

    @Override
    public void releaseFunds(Payment p, PaymentSaga saga, String reason) {
        send(p, saga, new ReleaseFundsV1(p.id().toString(), p.payerAccountId().toString(), amount(p),
                p.amount().currencyCode(), reason));
    }

    private void send(Payment p, PaymentSaga saga, Object command) {
        outbox.append(OUTBOX, Producers.PAYMENT, OutgoingMessage.forSaga(command, p.id().toString(), saga.sagaId()));
    }

    private static String amount(Payment p) {
        return p.amount().amount().toPlainString();
    }
}
