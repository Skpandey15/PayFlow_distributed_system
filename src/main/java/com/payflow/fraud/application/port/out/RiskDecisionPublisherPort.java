package com.payflow.fraud.application.port.out;

import com.payflow.fraud.domain.FraudAssessment;

/**
 * Announces a risk decision to the payment saga. The assessment lives in MongoDB, so there is no PostgreSQL
 * transaction to attach an outbox row to. The implementation publishes synchronously, and the triggering command's
 * offset is only committed afterwards (consume-process-produce, at-least-once).
 */
public interface RiskDecisionPublisherPort {

    void publish(FraudAssessment assessment);
}
