package com.payflow.fraud.application.port.in;

import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.shared.application.Actor;

import java.util.UUID;

/** Analyst-facing read of the full assessment evidence. */
public interface GetFraudAssessmentUseCase {

    FraudAssessment getByPaymentId(Actor actor, UUID paymentId);
}
