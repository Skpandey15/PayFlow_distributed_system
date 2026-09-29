package com.payflow.fraud.adapter.out.messaging;

import com.payflow.contracts.Producers;
import com.payflow.contracts.fraud.FraudMessages.RiskAssessedV2;
import com.payflow.fraud.application.port.out.RiskDecisionPublisherPort;
import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.fraud.domain.RiskDecision;
import com.payflow.fraud.domain.RiskSignal;
import com.payflow.platform.messaging.DirectEventPublisher;
import com.payflow.platform.messaging.OutgoingMessage;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code RiskAssessed} v2. It carries decision, score and signal codes, but <b>not</b> the underlying
 * evidence (IP addresses, device ids), which stays inside the Fraud context (data minimisation).
 */
@Component
class KafkaRiskDecisionPublisher implements RiskDecisionPublisherPort {

    private final DirectEventPublisher publisher;

    KafkaRiskDecisionPublisher(DirectEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(FraudAssessment a) {
        boolean approved = a.decision() == RiskDecision.APPROVE;
        RiskAssessedV2 payload = new RiskAssessedV2(a.paymentId().toString(), approved, a.riskScore(),
                approved ? null : a.declineReason(), a.signals().stream().map(RiskSignal::code).toList(),
                a.modelVersion());
        publisher.publish(Producers.FRAUD, OutgoingMessage.of(payload, "Payment", a.paymentId().toString()));
    }
}
