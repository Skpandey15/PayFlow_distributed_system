package com.payflow.fraud.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Everything the risk rules may look at. History-derived facts (velocity) are gathered by the
 * application layer through ports, so the rules themselves stay pure and deterministic.
 */
public record RiskEvaluationContext(
        UUID paymentId,
        AccountId payerAccountId,
        AccountId payeeAccountId,
        Money amount,
        String paymentMethod,
        ChannelContext channel,
        long payerAssessmentsInVelocityWindow) {

    public RiskEvaluationContext {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(payerAccountId, "payerAccountId");
        Objects.requireNonNull(amount, "amount");
        channel = channel == null ? ChannelContext.EMPTY : channel;
    }
}
