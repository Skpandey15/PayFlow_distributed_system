package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Page<PaymentJpaEntity> findByInitiatedBy(String initiatedBy, Pageable pageable);

    Page<PaymentJpaEntity> findByInitiatedByAndStatus(String initiatedBy, PaymentStatus status, Pageable pageable);

    Page<PaymentJpaEntity> findByStatus(PaymentStatus status, Pageable pageable);
}
