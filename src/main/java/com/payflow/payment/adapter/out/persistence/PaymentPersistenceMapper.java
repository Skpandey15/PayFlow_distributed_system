package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentSnapshot;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

/** Maps between the domain aggregate and its JPA representation. The only place that knows both. */
final class PaymentPersistenceMapper {

    private PaymentPersistenceMapper() {
    }

    static PaymentJpaEntity toNewEntity(Payment payment) {
        PaymentSnapshot s = payment.snapshot();
        PaymentJpaEntity entity = new PaymentJpaEntity(s.id().value(), s.payerAccountId().value(),
                s.payeeAccountId().value(), s.amount().amount(), s.amount().currencyCode(), s.method(),
                s.reference(), s.initiatedBy(), s.createdAt());
        entity.applyLifecycleState(s.status(), s.failureReason(), s.updatedAt());
        return entity;
    }

    /** Copies the only mutable part of the aggregate, its lifecycle state, onto a managed entity. */
    static void copyLifecycleState(Payment payment, PaymentJpaEntity entity) {
        entity.applyLifecycleState(payment.status(), payment.failureReason(), payment.updatedAt());
    }

    static Payment toDomain(PaymentJpaEntity e) {
        return Payment.rehydrate(new PaymentSnapshot(
                new PaymentId(e.getId()),
                new AccountId(e.getPayerAccountId()),
                new AccountId(e.getPayeeAccountId()),
                Money.of(e.getAmount(), Money.currency(e.getCurrency())),
                e.getMethod(),
                e.getReference(),
                e.getInitiatedBy(),
                e.getStatus(),
                e.getFailureReason(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion()));
    }
}
