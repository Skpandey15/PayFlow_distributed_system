package com.payflow.settlement.adapter.out.persistence;

import com.payflow.platform.persistence.PersistenceErrors;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayUnavailableException;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort;
import com.payflow.settlement.domain.Settlement;
import com.payflow.settlement.domain.SettlementRail;
import com.payflow.settlement.domain.SettlementSnapshot;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaSettlementRepositoryAdapter implements SettlementRepositoryPort {

    static final String PAYMENT_UNIQUE = "uq_settlement_payment";

    private final SpringDataSettlementRepository repository;
    private final EntityManager entityManager;

    JpaSettlementRepositoryAdapter(SpringDataSettlementRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void add(Settlement settlement) {
        SettlementSnapshot s = settlement.snapshot();
        SettlementJpaEntity entity = new SettlementJpaEntity(s.id(), s.paymentId(), s.rail(), s.amount().amount(),
                s.amount().currencyCode(), s.paymentReference(), s.createdAt());
        entity.applyState(s);
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (PersistenceException e) {
            if (PersistenceErrors.isUniqueViolation(e, PAYMENT_UNIQUE)) {
                throw new DuplicateSettlementException(s.paymentId(), e);
            }
            throw e;
        }
    }

    @Override
    public void update(Settlement settlement) {
        SettlementJpaEntity entity = repository.findById(settlement.id())
                .orElseThrow(() -> new IllegalStateException("Cannot update unknown settlement " + settlement.id()));
        if (entity.getVersion() != settlement.version()) {
            throw new ConcurrencyConflictException("Settlement " + settlement.id() + " was modified concurrently", null);
        }
        entity.applyState(settlement.snapshot());
        try {
            repository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException("Settlement " + settlement.id() + " was modified concurrently", e);
        }
    }

    @Override
    public Optional<Settlement> findByPaymentId(UUID paymentId) {
        return repository.findByPaymentId(paymentId).map(e -> Settlement.rehydrate(e.toSnapshot()));
    }

    @Override
    public List<UUID> claimParked(SettlementRail rail, int limit, Duration idle) {
        return repository.claimParked(rail.name(), GatewayUnavailableException.CIRCUIT_OPEN, idle.toMillis() / 1000.0, limit);
    }
}
