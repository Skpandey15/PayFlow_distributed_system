package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.SagaStep;

import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentSagaRepositoryPort {

    void add(PaymentSaga saga);

    /** Optimistic-lock update (ConcurrencyConflictException if another transaction advanced the saga). */
    void update(PaymentSaga saga);

    Optional<PaymentSaga> findByPaymentId(PaymentId paymentId);

    /**
     * Locks up to {@code limit} in-flight sagas whose current step started before {@code startedBefore}
     * ({@code FOR UPDATE SKIP LOCKED}), so several replicas running recovery never pick the same saga.
     */
    List<PaymentSaga> lockInFlightStartedBefore(Instant startedBefore, int limit);

    /** Sagas awaiting manual review, oldest escalation first. */
    PageResult<PaymentSaga> findInManualReview(PageQuery page);

    /** Per non-terminal step (in flight and MANUAL_REVIEW): number of sagas and the oldest step start. */
    List<StepCount> countOpenByStep();

    record StepCount(SagaStep step, long count, Instant oldestStepStartedAt) {
    }
}
