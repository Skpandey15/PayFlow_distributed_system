package com.payflow.fraud.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Identifiers;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of one risk assessment for one payment: score, decision, and the evidence behind it.
 * At most one assessment exists per payment (re-assessment returns the existing record, which keeps
 * authorization retries idempotent and the decision stable).
 */
public record FraudAssessment(
        UUID id,
        UUID paymentId,
        AccountId payerAccountId,
        AccountId payeeAccountId,
        Money amount,
        String paymentMethod,
        int riskScore,
        RiskDecision decision,
        List<RiskSignal> signals,
        ChannelContext channel,
        String modelVersion,
        Instant assessedAt) {

    public FraudAssessment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(decision, "decision");
        signals = List.copyOf(signals);
        channel = channel == null ? ChannelContext.EMPTY : channel;
    }

    static FraudAssessment record(RiskEvaluationContext ctx, int score, RiskDecision decision,
                                  List<RiskSignal> signals, String modelVersion, Instant now) {
        return new FraudAssessment(Identifiers.timeOrderedUuid(), ctx.paymentId(), ctx.payerAccountId(),
                ctx.payeeAccountId(), ctx.amount(), ctx.paymentMethod(), score, decision, signals, ctx.channel(),
                modelVersion, now);
    }

    /** Stable, human-readable reason used when a decline is propagated to the payment. */
    public String declineReason() {
        return signals.isEmpty() ? "RISK_DECLINED"
                : "RISK_DECLINED:" + String.join(",", signals.stream().map(RiskSignal::code).toList());
    }
}
