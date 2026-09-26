package com.payflow.fraud.domain;

import java.util.Optional;

/**
 * Strategy for one independent fraud heuristic. New rules are added as new implementations and
 * registered in the policy; existing rules and the scoring policy do not change (Open/Closed).
 */
public interface RiskRule {

    /** Returns a signal if this rule fires for the given context, otherwise empty. */
    Optional<RiskSignal> evaluate(RiskEvaluationContext context);
}
