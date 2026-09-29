package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;

import java.util.Optional;

/**
 * Collection-like access to Payment aggregates (Repository pattern). The port speaks only domain types;
 * how aggregates are stored (JPA entities, SQL, another service) is the adapter's business.
 */
public interface PaymentRepositoryPort {

    void add(Payment payment);

    /**
     * Persists the aggregate's state change using optimistic locking against {@link Payment#version()}.
     *
     * @throws com.payflow.shared.application.ConcurrencyConflictException if another transaction changed it first
     */
    void update(Payment payment);

    Optional<Payment> findById(PaymentId id);

    /**
     * @param initiatedBy restrict to one initiator, or {@code null} for all (admin)
     * @param status      restrict to one status, or {@code null} for all
     */
    PageResult<Payment> search(String initiatedBy, PaymentStatus status, PageQuery page);
}
