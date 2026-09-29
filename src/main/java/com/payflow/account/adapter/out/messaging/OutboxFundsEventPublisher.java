package com.payflow.account.adapter.out.messaging;

import com.payflow.account.application.port.out.DepositRepositoryPort.DepositRecord;
import com.payflow.account.application.port.out.FundsEventPublisherPort;
import com.payflow.account.domain.FundsReservation;
import com.payflow.contracts.Producers;
import com.payflow.contracts.funds.FundsMessages.FundsCapturedV1;
import com.payflow.contracts.funds.FundsMessages.FundsDepositedV1;
import com.payflow.contracts.funds.FundsMessages.FundsReleasedV1;
import com.payflow.contracts.funds.FundsMessages.FundsReservationFailedV1;
import com.payflow.contracts.funds.FundsMessages.FundsReservedV1;
import com.payflow.platform.messaging.OutgoingMessage;
import com.payflow.platform.messaging.outbox.OutboxWriter;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Maps funds outcomes to the published {@code funds.events} contract and writes them to the Account outbox. */
@Component
class OutboxFundsEventPublisher implements FundsEventPublisherPort {

    static final String OUTBOX = "account.outbox_event";

    private final OutboxWriter outbox;

    OutboxFundsEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void fundsReserved(FundsReservation r) {
        emit(r.paymentId(), new FundsReservedV1(r.paymentId().toString(), r.id().toString(),
                r.payerAccountId().toString(), amount(r.amount()), r.amount().currencyCode()));
    }

    @Override
    public void reservationFailed(UUID paymentId, String reason) {
        emit(paymentId, new FundsReservationFailedV1(paymentId.toString(), reason));
    }

    @Override
    public void fundsCaptured(FundsReservation r) {
        emit(r.paymentId(), new FundsCapturedV1(r.paymentId().toString(), r.payerAccountId().toString(),
                r.payeeAccountId().toString(), amount(r.amount()), r.amount().currencyCode()));
    }

    @Override
    public void fundsReleased(UUID paymentId, AccountId payerAccountId, Money released, String reason) {
        emit(paymentId, new FundsReleasedV1(paymentId.toString(), amount(released), released.currencyCode(), reason));
    }

    @Override
    public void fundsDeposited(DepositRecord d) {
        outbox.append(OUTBOX, Producers.ACCOUNT, OutgoingMessage.of(
                new FundsDepositedV1(d.depositId().toString(), d.accountId().toString(), amount(d.amount()),
                        d.amount().currencyCode()),
                "Account", d.accountId().toString()));
    }

    private void emit(UUID paymentId, Object payload) {
        outbox.append(OUTBOX, Producers.ACCOUNT, OutgoingMessage.of(payload, "Payment", paymentId.toString()));
    }

    private static String amount(Money money) {
        return money.amount().toPlainString();
    }
}
