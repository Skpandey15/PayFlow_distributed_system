package com.payflow.account.adapter.out.persistence;

import com.payflow.account.application.port.out.FundsReservationRepositoryPort;
import com.payflow.account.domain.FundsReservation;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaFundsReservationRepositoryAdapter implements FundsReservationRepositoryPort {

    private final SpringDataFundsReservationRepository repository;
    private final EntityManager entityManager;

    JpaFundsReservationRepositoryAdapter(SpringDataFundsReservationRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<FundsReservation> findByPaymentId(UUID paymentId) {
        return repository.findByPaymentId(paymentId).map(JpaFundsReservationRepositoryAdapter::toDomain);
    }

    @Override
    public void add(FundsReservation r) {
        entityManager.persist(new FundsReservationJpaEntity(r.id(), r.paymentId(), r.payerAccountId().value(),
                r.payeeAccountId() == null ? null : r.payeeAccountId().value(), r.amount().amount(),
                r.amount().currencyCode(), r.status(), r.reason(), r.createdAt(), r.updatedAt()));
        entityManager.flush();
    }

    @Override
    public void update(FundsReservation r) {
        FundsReservationJpaEntity entity = repository.findById(r.id()).orElseThrow();
        if (entity.getVersion() != r.version()) {
            throw new ConcurrencyConflictException("Reservation " + r.id() + " changed concurrently", null);
        }
        entity.apply(r.status(), r.reason(), r.updatedAt());
        repository.saveAndFlush(entity);
    }

    private static FundsReservation toDomain(FundsReservationJpaEntity e) {
        Currency currency = Money.currency(e.getCurrency());
        return FundsReservation.rehydrate(e.getId(), e.getPaymentId(), new AccountId(e.getPayerAccountId()),
                e.getPayeeAccountId() == null ? null : new AccountId(e.getPayeeAccountId()),
                Money.of(e.getAmount(), currency), e.getStatus(), e.getReason(), e.getVersion(), e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
