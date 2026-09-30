package com.payflow.payment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataPaymentSagaRepository extends JpaRepository<PaymentSagaJpaEntity, UUID> {

    Optional<PaymentSagaJpaEntity> findByPaymentId(UUID paymentId);

    /** SKIP LOCKED: concurrent scanners on other replicas silently skip sagas this one is recovering. */
    @Query(value = """
            select * from payment.payment_saga
             where step in ('AWAITING_RISK', 'AWAITING_FUNDS', 'AWAITING_SETTLEMENT', 'AWAITING_CAPTURE', 'COMPENSATING')
               and step_started_at < :startedBefore
             order by step_started_at
             limit :limit
             for update skip locked""", nativeQuery = true)
    List<PaymentSagaJpaEntity> lockInFlightStartedBefore(@Param("startedBefore") Instant startedBefore,
                                                        @Param("limit") int limit);

    /** Monitoring: open sagas per step. The in-flight part is served by ix_payment_saga_in_flight. */
    @Query(value = """
            select step, count(*) as open_count, min(step_started_at) as oldest
              from payment.payment_saga
             where step in ('AWAITING_RISK', 'AWAITING_FUNDS', 'AWAITING_SETTLEMENT', 'AWAITING_CAPTURE', 'COMPENSATING',
                            'MANUAL_REVIEW')
             group by step""", nativeQuery = true)
    List<Object[]> countOpenByStep();

    /** Review queue, oldest escalation first (ix_payment_saga_manual_review). */
    @Query(value = "select * from payment.payment_saga where step = 'MANUAL_REVIEW' order by step_started_at "
            + "limit :limit offset :offset", nativeQuery = true)
    List<PaymentSagaJpaEntity> findInManualReview(@Param("limit") int limit, @Param("offset") long offset);

    @Query(value = "select count(*) from payment.payment_saga where step = 'MANUAL_REVIEW'", nativeQuery = true)
    long countInManualReview();

    /** Admission signal, read once per second per replica: a subset of ix_payment_saga_in_flight's predicate. */
    @Query(value = "select count(*) from payment.payment_saga"
            + " where step in ('AWAITING_RISK', 'AWAITING_FUNDS', 'AWAITING_CAPTURE', 'COMPENSATING')", nativeQuery = true)
    long countInPipeline();
}
