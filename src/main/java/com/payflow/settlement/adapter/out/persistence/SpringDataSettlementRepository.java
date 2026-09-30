package com.payflow.settlement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataSettlementRepository extends JpaRepository<SettlementJpaEntity, UUID> {

    Optional<SettlementJpaEntity> findByPaymentId(UUID paymentId);

    /**
     * Claim = refresh last_attempt_at on rows nobody else holds (SKIP LOCKED) and that were idle long enough. The
     * version is not bumped: the claim is bookkeeping, not a state change, and the next domain update overwrites it.
     */
    @Modifying
    @Query(value = """
            update settlement.settlement set last_attempt_at = now()
             where id in (select id from settlement.settlement
                           where status = 'PENDING' and rail = :rail and last_error_code = :errorCode
                             and last_attempt_at < now() - make_interval(secs => :idleSeconds)
                           order by last_attempt_at
                           limit :limit
                           for update skip locked)
            returning payment_id
            """, nativeQuery = true)
    List<UUID> claimParked(String rail, String errorCode, double idleSeconds, int limit);
}
