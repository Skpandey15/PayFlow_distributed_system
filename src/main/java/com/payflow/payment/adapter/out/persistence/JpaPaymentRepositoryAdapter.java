package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * PostgreSQL implementation of {@link PaymentRepositoryPort}.
 *
 * <p>Optimistic locking happens in two layers:
 * <ol>
 *   <li>An explicit check that the stored version still equals the version the aggregate was loaded with.
 *       This catches changes made between an earlier read transaction and this write transaction.</li>
 *   <li>Hibernate's {@code UPDATE ... WHERE version = ?} at flush. This catches a concurrent commit
 *       between our read and our flush within this transaction.</li>
 * </ol>
 * Either failure becomes a {@link ConcurrencyConflictException}; nothing is overwritten.
 */
@Component
class JpaPaymentRepositoryAdapter implements PaymentRepositoryPort {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final SpringDataPaymentRepository repository;

    JpaPaymentRepositoryAdapter(SpringDataPaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(Payment payment) {
        repository.saveAndFlush(PaymentPersistenceMapper.toNewEntity(payment));
    }

    @Override
    public void update(Payment payment) {
        PaymentJpaEntity entity = repository.findById(payment.id().value())
                .orElseThrow(() -> new IllegalStateException("Cannot update unknown payment " + payment.id()));
        if (entity.getVersion() != payment.version()) {
            throw conflict(payment, null);
        }
        PaymentPersistenceMapper.copyLifecycleState(payment, entity);
        try {
            repository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException e) {
            throw conflict(payment, e);
        }
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return repository.findById(id.value()).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    public PageResult<Payment> search(String initiatedBy, PaymentStatus status, PageQuery page) {
        PageRequest pageable = PageRequest.of(page.page(), page.size(), NEWEST_FIRST);
        Page<PaymentJpaEntity> result;
        if (initiatedBy != null && status != null) {
            result = repository.findByInitiatedByAndStatus(initiatedBy, status, pageable);
        } else if (initiatedBy != null) {
            result = repository.findByInitiatedBy(initiatedBy, pageable);
        } else if (status != null) {
            result = repository.findByStatus(status, pageable);
        } else {
            result = repository.findAll(pageable);
        }
        return new PageResult<>(result.getContent().stream().map(PaymentPersistenceMapper::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }

    private static ConcurrencyConflictException conflict(Payment payment, Throwable cause) {
        return new ConcurrencyConflictException(
                "Payment " + payment.id() + " was modified concurrently; reload and retry", cause);
    }
}
