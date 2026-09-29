package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.application.port.out.IdempotencyStorePort;
import com.payflow.payment.domain.PaymentId;
import com.payflow.platform.persistence.PersistenceErrors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Idempotency records in PostgreSQL.
 *
 * <p>{@link #add} uses {@code persist} + {@code flush} (always an INSERT), never {@code merge}. A merge would
 * SELECT first and silently UPDATE a row committed by a concurrent request, overwriting its fingerprint.
 * An INSERT lets the primary key arbitrate, and the flush surfaces the violation here, inside the adapter,
 * where it can be translated into the port's {@link IdempotencyKeyConflictException}.
 */
@Component
class JpaIdempotencyStoreAdapter implements IdempotencyStorePort {

    static final String PRIMARY_KEY = "pk_idempotency_record";

    private final EntityManager entityManager;

    JpaIdempotencyStoreAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<IdempotencyRecord> find(String clientId, String idempotencyKey) {
        return Optional.ofNullable(entityManager.find(IdempotencyRecordJpaEntity.class,
                        new IdempotencyRecordJpaEntity.Key(clientId, idempotencyKey)))
                .map(e -> new IdempotencyRecord(e.getKey().getClientId(), e.getKey().getIdempotencyKey(),
                        e.getRequestFingerprint(), new PaymentId(e.getPaymentId()), e.getCreatedAt(), e.getExpiresAt()));
    }

    @Override
    public void add(IdempotencyRecord record) {
        IdempotencyRecordJpaEntity entity = new IdempotencyRecordJpaEntity(
                new IdempotencyRecordJpaEntity.Key(record.clientId(), record.idempotencyKey()),
                record.requestFingerprint(), record.paymentId().value(), record.createdAt(), record.expiresAt());
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (PersistenceException e) {
            if (PersistenceErrors.isUniqueViolation(e, PRIMARY_KEY)) {
                throw new IdempotencyKeyConflictException(record.idempotencyKey(), e);
            }
            throw e;
        }
    }

    @Override
    public int deleteExpired(Instant now) {
        return entityManager.createQuery("delete from IdempotencyRecordJpaEntity r where r.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();
    }
}
