package com.payflow.fraud.adapter.out.persistence;

import com.payflow.fraud.adapter.out.persistence.FraudAssessmentDocument.Channel;
import com.payflow.fraud.adapter.out.persistence.FraudAssessmentDocument.Signal;
import com.payflow.fraud.domain.ChannelContext;
import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.fraud.domain.RiskDecision;
import com.payflow.fraud.domain.RiskSignal;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.bson.types.Decimal128;

import java.util.List;
import java.util.UUID;

final class FraudAssessmentDocumentMapper {

    private FraudAssessmentDocumentMapper() {
    }

    static FraudAssessmentDocument toDocument(FraudAssessment a) {
        ChannelContext c = a.channel();
        return new FraudAssessmentDocument(a.id().toString(), a.paymentId().toString(),
                a.payerAccountId().toString(), a.payeeAccountId() == null ? null : a.payeeAccountId().toString(),
                new Decimal128(a.amount().amount()), a.amount().currencyCode(), a.paymentMethod(), a.riskScore(),
                a.decision().name(),
                a.signals().stream().map(s -> new Signal(s.code(), s.score(), s.detail())).toList(),
                new Channel(c.deviceId(), c.ipAddress(), c.userAgent(), c.countryCode()),
                a.modelVersion(), a.assessedAt());
    }

    static FraudAssessment toDomain(FraudAssessmentDocument d) {
        List<RiskSignal> signals = d.getSignals() == null ? List.of()
                : d.getSignals().stream().map(s -> new RiskSignal(s.code(), s.score(), s.detail())).toList();
        Channel c = d.getChannel();
        ChannelContext channel = c == null ? ChannelContext.EMPTY
                : new ChannelContext(c.deviceId(), c.ipAddress(), c.userAgent(), c.countryCode());
        return new FraudAssessment(
                UUID.fromString(d.getId()),
                UUID.fromString(d.getPaymentId()),
                AccountId.of(d.getPayerAccountId()),
                d.getPayeeAccountId() == null ? null : AccountId.of(d.getPayeeAccountId()),
                Money.of(d.getAmount().bigDecimalValue(), Money.currency(d.getCurrency())),
                d.getPaymentMethod(),
                d.getRiskScore(),
                RiskDecision.valueOf(d.getDecision()),
                signals,
                channel,
                d.getModelVersion(),
                d.getAssessedAt());
    }
}
