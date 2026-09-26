package com.payflow.payment.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment lifecycle, as a table-driven state machine.
 *
 * <pre>
 *   CREATED ──authorize──▶ AUTHORIZED ──startProcessing──▶ PROCESSING ──▶ SETTLED
 *      │  └──reject──▶ REJECTED        │                         └──────▶ FAILED
 *      └──cancel──▶ CANCELLED ◀──cancel┘
 * </pre>
 *
 * <p>Semantics (see WP-01-LLD for why the originally proposed VALIDATING state was dropped):
 * <ul>
 *   <li>{@code REJECTED}: a business decision (risk decline, ineligible account). Do not retry the same payment.</li>
 *   <li>{@code FAILED}: the settlement rail declined or failed after submission. The payer may retry with a new payment.</li>
 *   <li>{@code CANCELLED}: withdrawn by the payer before funds were in flight. After PROCESSING, reversal is a refund (future).</li>
 * </ul>
 */
public enum PaymentStatus {

    CREATED,
    AUTHORIZED,
    REJECTED,
    CANCELLED,
    PROCESSING,
    SETTLED,
    FAILED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(CREATED, EnumSet.of(AUTHORIZED, REJECTED, CANCELLED));
        ALLOWED_TRANSITIONS.put(AUTHORIZED, EnumSet.of(PROCESSING, CANCELLED));
        ALLOWED_TRANSITIONS.put(PROCESSING, EnumSet.of(SETTLED, FAILED));
        ALLOWED_TRANSITIONS.put(REJECTED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(SETTLED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
