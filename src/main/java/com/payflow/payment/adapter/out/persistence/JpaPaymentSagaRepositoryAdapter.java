package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.CheckoutContext;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.PaymentSagaSnapshot;
import com.payflow.payment.domain.saga.SagaStep;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class JpaPaymentSagaRepositoryAdapter implements PaymentSagaRepositoryPort {

    private final SpringDataPaymentSagaRepository repository;

    JpaPaymentSagaRepositoryAdapter(SpringDataPaymentSagaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(PaymentSaga saga) {
        PaymentSagaSnapshot s = saga.snapshot();
        CheckoutContext c = s.checkout();
        PaymentSagaJpaEntity entity = new PaymentSagaJpaEntity(s.sagaId(), s.paymentId().value(), s.correlationId(),
                c.deviceId(), c.ipAddress(), c.userAgent(), c.countryCode(), s.createdAt());
        entity.apply(s.step(), s.compensationReason(), s.outcomeReason(), s.stepAttempts(), s.stepStartedAt(),
                s.updatedAt(), s.escalatedFrom());
        repository.saveAndFlush(entity);
    }

    @Override
    public void update(PaymentSaga saga) {
        PaymentSagaSnapshot s = saga.snapshot();
        PaymentSagaJpaEntity entity = repository.findById(s.sagaId()).orElseThrow();
        if (entity.getVersion() != s.version()) {
            throw new ConcurrencyConflictException("Saga " + s.sagaId() + " advanced concurrently", null);
        }
        entity.apply(s.step(), s.compensationReason(), s.outcomeReason(), s.stepAttempts(), s.stepStartedAt(),
                s.updatedAt(), s.escalatedFrom());
        try {
            repository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException("Saga " + s.sagaId() + " advanced concurrently", e);
        }
    }

    @Override
    public Optional<PaymentSaga> findByPaymentId(PaymentId paymentId) {
        return repository.findByPaymentId(paymentId.value()).map(JpaPaymentSagaRepositoryAdapter::toDomain);
    }

    @Override
    public List<PaymentSaga> lockInFlightStartedBefore(Instant startedBefore, int limit) {
        return repository.lockInFlightStartedBefore(startedBefore, limit).stream()
                .map(JpaPaymentSagaRepositoryAdapter::toDomain).toList();
    }

    @Override
    public PageResult<PaymentSaga> findInManualReview(PageQuery page) {
        return new PageResult<>(repository.findInManualReview(page.size(), (long) page.page() * page.size()).stream()
                .map(JpaPaymentSagaRepositoryAdapter::toDomain).toList(), page.page(), page.size(),
                repository.countInManualReview());
    }

    @Override
    public List<StepCount> countOpenByStep() {
        return repository.countOpenByStep().stream()
                .map(r -> new StepCount(SagaStep.valueOf((String) r[0]), ((Number) r[1]).longValue(), toInstant(r[2])))
                .toList();
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case Instant i -> i;
            case java.time.OffsetDateTime o -> o.toInstant();
            case java.sql.Timestamp t -> t.toInstant();
            default -> throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
        };
    }

    private static PaymentSaga toDomain(PaymentSagaJpaEntity e) {
        return PaymentSaga.rehydrate(new PaymentSagaSnapshot(e.getSagaId(), new PaymentId(e.getPaymentId()), e.getStep(),
                e.getCompensationReason(), e.getOutcomeReason(), e.getStepAttempts(), e.getStepStartedAt(),
                e.getCorrelationId(), new CheckoutContext(e.getCheckoutDeviceId(), e.getCheckoutIpAddress(),
                e.getCheckoutUserAgent(), e.getCheckoutCountry()), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getEscalatedFrom()));
    }
}
