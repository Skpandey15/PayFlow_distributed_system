package com.payflow.fraud.domain;

import java.util.Objects;

/** One piece of evidence contributing to a risk score. {@code score} is the rule's contribution (0-100). */
public record RiskSignal(String code, int score, String detail) {

    public RiskSignal {
        Objects.requireNonNull(code, "code");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0..100");
        }
    }
}
