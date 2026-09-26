package com.payflow.fraud.domain;

/**
 * Outcome of a risk assessment. A manual-review band (REVIEW) is intentionally absent in WP-01: it needs
 * an asynchronous case-management workflow that can resume the payment later (WP-02 event-driven flow).
 */
public enum RiskDecision {
    APPROVE,
    DECLINE
}
