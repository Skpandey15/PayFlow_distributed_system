package com.payflow.settlement.application.port.out;

import com.payflow.settlement.domain.Settlement;
import com.payflow.settlement.domain.SettlementRail;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepositoryPort {

    /**
     * @throws DuplicateSettlementException if a settlement for the same payment was committed concurrently
     */
    void add(Settlement settlement);

    /** Persists state changes with optimistic locking. */
    void update(Settlement settlement);

    Optional<Settlement> findByPaymentId(UUID paymentId);

    /**
     * Claims up to {@code limit} settlements of {@code rail} that are PENDING because the circuit was open, oldest
     * first, untouched for at least {@code idle}. Claiming refreshes their last-attempt time, so a concurrent replica
     * (or the next run) skips them for {@code idle}: the same instruction is not re-submitted twice at once.
     *
     * @return the claimed settlements' payment ids
     */
    List<UUID> claimParked(SettlementRail rail, int limit, Duration idle);

    class DuplicateSettlementException extends RuntimeException {
        public DuplicateSettlementException(UUID paymentId, Throwable cause) {
            super("Settlement already exists for payment " + paymentId, cause);
        }
    }
}
