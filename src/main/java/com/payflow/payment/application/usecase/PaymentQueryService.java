package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.GetPaymentUseCase;
import com.payflow.payment.application.port.in.ListPaymentsUseCase;
import com.payflow.payment.application.port.in.PaymentView;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;
import com.payflow.shared.application.TransactionRunner;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

public class PaymentQueryService implements GetPaymentUseCase, ListPaymentsUseCase {

    private final PaymentRepositoryPort payments;
    private final TransactionRunner tx;

    public PaymentQueryService(PaymentRepositoryPort payments, TransactionRunner tx) {
        this.payments = payments;
        this.tx = tx;
    }

    @Override
    public PaymentView get(Actor actor, PaymentId paymentId) {
        requirePermission(actor, PaymentPermissions.READ);
        return PaymentView.from(tx.readOnly(() -> PaymentAccess.loadVisible(payments, actor, paymentId)));
    }

    @Override
    public PageResult<PaymentView> list(Actor actor, PaymentStatus statusFilter, PageQuery page) {
        requirePermission(actor, PaymentPermissions.READ);
        String initiatorFilter = actor.hasPermission(PaymentPermissions.ADMIN) ? null : actor.subject();
        return tx.readOnly(() -> payments.search(initiatorFilter, statusFilter, page)).map(PaymentView::from);
    }
}
