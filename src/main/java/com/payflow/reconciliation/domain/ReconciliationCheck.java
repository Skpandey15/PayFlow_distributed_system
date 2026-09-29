package com.payflow.reconciliation.domain;

/**
 * The financial invariants PayFlow continuously verifies (WP-03, K4). Each names what must hold between two
 * independently written records; a violation is drift, and drift is reported, never silently repaired.
 */
public enum ReconciliationCheck {

    /** account_balance.reserved = sum of that account's RESERVED reservations (same-transaction invariant). */
    RESERVED_MATCHES_RESERVATIONS(Severity.CRITICAL, false),
    /** available + reserved = ledger balance (credits − debits) for every settled account. */
    LEDGER_MATCHES_BALANCE(Severity.CRITICAL, true),
    /** every CAPTURED reservation has its ledger journal payment:{id}:settlement. */
    CAPTURE_POSTED_TO_LEDGER(Severity.HIGH, true),
    /** every settlement journal in the ledger has a CAPTURED reservation (the ledger never invents money movement). */
    LEDGER_POSTING_HAS_CAPTURE(Severity.CRITICAL, true),
    /** every deposit has its ledger journal deposit:{id}. */
    DEPOSIT_POSTED_TO_LEDGER(Severity.HIGH, true),
    /** a SETTLED payment has a COMPLETED settlement. */
    SETTLED_PAYMENT_HAS_COMPLETED_SETTLEMENT(Severity.CRITICAL, false),
    /** a COMPLETED settlement (money moved at the rail) belongs to a SETTLED payment or one still finishing / in review. */
    COMPLETED_SETTLEMENT_HAS_SETTLED_PAYMENT(Severity.CRITICAL, true),
    /** a payment that ended without settling holds no funds any more (the release happened). */
    NO_HOLD_ON_FINISHED_PAYMENT(Severity.HIGH, true),
    /** double entry: for every currency the whole ledger sums to zero. */
    LEDGER_ZERO_SUM(Severity.CRITICAL, false);

    private final Severity severity;
    private final boolean eventuallyConsistent;

    ReconciliationCheck(Severity severity, boolean eventuallyConsistent) {
        this.severity = severity;
        this.eventuallyConsistent = eventuallyConsistent;
    }

    public Severity severity() {
        return severity;
    }

    /**
     * True when the two sides are written by different transactions (via events): records younger than the grace
     * period are excluded, so a message in flight is not reported as drift.
     */
    public boolean eventuallyConsistent() {
        return eventuallyConsistent;
    }
}
