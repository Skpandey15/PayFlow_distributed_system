package com.payflow.reconciliation.domain;

import java.math.BigDecimal;

/**
 * One invariant violation observed in one run. {@code subjectId} identifies the record for the investigator (it is
 * stored and shown to operators, never used as a metric label).
 */
public record Finding(ReconciliationCheck check, String subjectType, String subjectId, String currency,
                      BigDecimal expected, BigDecimal actual) {

    public BigDecimal absoluteDelta() {
        BigDecimal e = expected == null ? BigDecimal.ZERO : expected;
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        return e.subtract(a).abs();
    }
}
