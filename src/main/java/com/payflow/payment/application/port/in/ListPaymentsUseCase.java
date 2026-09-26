package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;

public interface ListPaymentsUseCase {

    /** Newest first. Non-admin callers only ever see payments they initiated. */
    PageResult<PaymentView> list(Actor actor, PaymentStatus statusFilter, PageQuery page);
}
