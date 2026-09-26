package com.payflow.fraud;

import com.payflow.fraud.adapter.out.persistence.FraudAssessmentDocument;
import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase;
import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase.AssessRiskCommand;
import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase.Channel;
import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase.RiskAssessmentView;
import com.payflow.fraud.application.port.out.FraudAssessmentRepositoryPort;
import com.payflow.fraud.application.port.out.FraudAssessmentRepositoryPort.DuplicateAssessmentException;
import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.IntegrationTest;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class FraudAssessmentMongoIT {

    @Autowired
    AssessPaymentRiskUseCase assess;
    @Autowired
    FraudAssessmentRepositoryPort repository;
    @Autowired
    MongoTemplate mongo;

    AssessRiskCommand command(UUID paymentId, AccountId payer, String amount, String country) {
        return new AssessRiskCommand(paymentId, payer, AccountId.newId(), Money.of(amount, "USD"), "CARD",
                new Channel("device-9", "192.0.2.10", "Mozilla/5.0", country));
    }

    @Test
    void assessmentIsStoredAsAVersionedDocumentWithExactDecimalMoney() {
        UUID paymentId = UUID.randomUUID();
        RiskAssessmentView view = assess.assess(command(paymentId, AccountId.newId(), "10000.00", "US"));

        Document raw = mongo.getCollection(FraudAssessmentDocument.COLLECTION)
                .find(new Document("paymentId", paymentId.toString())).first();
        assertThat(raw).isNotNull();
        assertThat(raw.get("amount")).isInstanceOf(Decimal128.class);
        assertThat(((Decimal128) raw.get("amount")).bigDecimalValue()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(raw.getInteger("schemaVersion")).isEqualTo(FraudAssessmentDocument.CURRENT_SCHEMA_VERSION);
        assertThat(raw.get("channel", Document.class).getString("ipAddress")).isEqualTo("192.0.2.10");
        assertThat(view.signalCodes()).contains("HIGH_AMOUNT");

        FraudAssessment loaded = repository.findByPaymentId(paymentId).orElseThrow();
        assertThat(loaded.amount()).isEqualTo(Money.of("10000.00", "USD"));
        assertThat(loaded.id()).isEqualTo(view.assessmentId());
    }

    @Test
    void reassessingTheSamePaymentReturnsTheOriginalDecision() {
        UUID paymentId = UUID.randomUUID();
        RiskAssessmentView first = assess.assess(command(paymentId, AccountId.newId(), "5.00", "US"));
        // Even with different evidence, the decision for this payment does not flip on retry.
        RiskAssessmentView second = assess.assess(command(paymentId, AccountId.newId(), "5.00", "KP"));

        assertThat(second.assessmentId()).isEqualTo(first.assessmentId());
        assertThat(second.approved()).isTrue();
        assertThat(mongo.count(Query.query(Criteria.where("paymentId").is(paymentId.toString())),
                FraudAssessmentDocument.class)).isEqualTo(1);
    }

    @Test
    void uniqueIndexRejectsASecondAssessmentForTheSamePayment() {
        UUID paymentId = UUID.randomUUID();
        assess.assess(command(paymentId, AccountId.newId(), "5.00", "US"));
        FraudAssessment duplicate = repository.findByPaymentId(paymentId).orElseThrow();
        FraudAssessment sameIdNewDoc = new FraudAssessment(UUID.randomUUID(), paymentId, duplicate.payerAccountId(),
                duplicate.payeeAccountId(), duplicate.amount(), "CARD", 0, duplicate.decision(), duplicate.signals(),
                duplicate.channel(), "rules-v1", Instant.now());

        assertThatThrownBy(() -> repository.save(sameIdNewDoc)).isInstanceOf(DuplicateAssessmentException.class);
    }

    @Test
    void velocityCountsOnlyTheSamePayerWithinTheWindow() {
        AccountId payer = AccountId.newId();
        Instant before = Instant.now().minusSeconds(1);
        for (int i = 0; i < 3; i++) {
            assess.assess(command(UUID.randomUUID(), payer, "1.00", "US"));
        }
        assess.assess(command(UUID.randomUUID(), AccountId.newId(), "1.00", "US"));

        assertThat(repository.countByPayerSince(payer, before)).isEqualTo(3);
        assertThat(repository.countByPayerSince(payer, Instant.now().plusSeconds(60))).isZero();
    }
}
