package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentSnapshot;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test fake with the same optimistic-locking contract as the JPA adapter (version check + increment). */
class InMemoryPaymentRepository implements PaymentRepositoryPort {

    final Map<PaymentId, PaymentSnapshot> rows = new ConcurrentHashMap<>();

    @Override
    public void add(Payment payment) {
        if (rows.putIfAbsent(payment.id(), payment.snapshot()) != null) {
            throw new IllegalStateException("duplicate id");
        }
    }

    @Override
    public synchronized void update(Payment payment) {
        PaymentSnapshot stored = rows.get(payment.id());
        if (stored.version() != payment.version()) {
            throw new ConcurrencyConflictException("stale", null);
        }
        PaymentSnapshot s = payment.snapshot();
        rows.put(payment.id(), new PaymentSnapshot(s.id(), s.payerAccountId(), s.payeeAccountId(), s.amount(),
                s.method(), s.reference(), s.initiatedBy(), s.status(), s.failureReason(), s.createdAt(),
                s.updatedAt(), s.version() + 1));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return Optional.ofNullable(rows.get(id)).map(Payment::rehydrate);
    }

    @Override
    public PageResult<Payment> search(String initiatedBy, PaymentStatus status, PageQuery page) {
        List<PaymentSnapshot> matching = rows.values().stream()
                .filter(s -> initiatedBy == null || s.initiatedBy().equals(initiatedBy))
                .filter(s -> status == null || s.status() == status)
                .sorted(Comparator.comparing(PaymentSnapshot::createdAt).reversed())
                .toList();
        List<Payment> items = matching.stream().skip((long) page.page() * page.size()).limit(page.size())
                .map(Payment::rehydrate).toList();
        return new PageResult<>(items, page.page(), page.size(), matching.size());
    }

    PaymentStatus statusOf(PaymentId id) {
        return rows.get(id).status();
    }
}
