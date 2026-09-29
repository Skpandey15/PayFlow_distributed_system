package com.payflow.account.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataFundsReservationRepository extends JpaRepository<FundsReservationJpaEntity, UUID> {

    Optional<FundsReservationJpaEntity> findByPaymentId(UUID paymentId);
}
