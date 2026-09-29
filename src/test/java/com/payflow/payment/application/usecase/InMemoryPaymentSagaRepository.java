package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.PaymentSagaSnapshot;
import com.payflow.shared.application.ConcurrencyConflictException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test fake with the same optimistic-locking contract as the JPA adapter. */
class InMemoryPaymentSagaRepository implements PaymentSagaRepositoryPort {

    final Map<PaymentId, PaymentSagaSnapshot> rows = new ConcurrentHashMap<>();

    @Override
    public void add(PaymentSaga saga) {
        rows.put(saga.paymentId(), saga.snapshot());
    }

    @Override
    public synchronized void update(PaymentSaga saga) {
        PaymentSagaSnapshot stored = rows.get(saga.paymentId());
        if (stored.version() != saga.version()) {
            throw new ConcurrencyConflictException("stale saga", null);
        }
        PaymentSagaSnapshot s = saga.snapshot();
        rows.put(saga.paymentId(), new PaymentSagaSnapshot(s.sagaId(), s.paymentId(), s.step(), s.compensationReason(),
                s.outcomeReason(), s.stepAttempts(), s.stepStartedAt(), s.correlationId(), s.checkout(),
                s.version() + 1, s.createdAt(), s.updatedAt(), s.escalatedFrom()));
    }

    @Override
    public Optional<PaymentSaga> findByPaymentId(PaymentId paymentId) {
        return Optional.ofNullable(rows.get(paymentId)).map(PaymentSaga::rehydrate);
    }

    @Override
    public List<PaymentSaga> lockInFlightStartedBefore(Instant startedBefore, int limit) {
        return rows.values().stream()
                .filter(s -> s.step().isInFlight() && s.stepStartedAt().isBefore(startedBefore))
                .sorted(Comparator.comparing(PaymentSagaSnapshot::stepStartedAt))
                .limit(limit)
                .map(PaymentSaga::rehydrate)
                .toList();
    }

    @Override
    public com.payflow.shared.application.PageResult<PaymentSaga> findInManualReview(
            com.payflow.shared.application.PageQuery page) {
        List<PaymentSaga> all = rows.values().stream()
                .filter(s -> s.step() == com.payflow.payment.domain.saga.SagaStep.MANUAL_REVIEW)
                .sorted(Comparator.comparing(PaymentSagaSnapshot::stepStartedAt)).map(PaymentSaga::rehydrate).toList();
        return new com.payflow.shared.application.PageResult<>(all.stream().skip((long) page.page() * page.size())
                .limit(page.size()).toList(), page.page(), page.size(), all.size());
    }

    @Override
    public List<StepCount> countOpenByStep() {
        return rows.values().stream().filter(s -> !s.step().isTerminal())
                .collect(java.util.stream.Collectors.groupingBy(PaymentSagaSnapshot::step))
                .entrySet().stream()
                .map(e -> new StepCount(e.getKey(), e.getValue().size(), e.getValue().stream()
                        .map(PaymentSagaSnapshot::stepStartedAt).min(Comparator.naturalOrder()).orElseThrow()))
                .toList();
    }

    PaymentSaga get(PaymentId id) {
        return PaymentSaga.rehydrate(rows.get(id));
    }
}
