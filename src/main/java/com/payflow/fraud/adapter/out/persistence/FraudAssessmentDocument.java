package com.payflow.fraud.adapter.out.persistence;

import org.bson.types.Decimal128;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB representation of an assessment (collection {@code fraud_assessments}).
 *
 * <ul>
 *   <li>{@code schemaVersion}: documents evolve as fraud teams add signals. Readers must tolerate older
 *       versions, and new fields are additive. This is the main reason the workload lives in a document store.</li>
 *   <li>Money is stored as {@link Decimal128} (exact IEEE-754 decimal), never as a double or a string.</li>
 *   <li>Ids are stored as strings so documents stay readable and portable across drivers.</li>
 * </ul>
 */
@Document(collection = FraudAssessmentDocument.COLLECTION)
public class FraudAssessmentDocument {

    public static final String COLLECTION = "fraud_assessments";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Id
    private String id;
    private int schemaVersion;
    @Field("paymentId")
    private String paymentId;
    private String payerAccountId;
    private String payeeAccountId;
    private Decimal128 amount;
    private String currency;
    private String paymentMethod;
    private int riskScore;
    private String decision;
    private List<Signal> signals;
    private Channel channel;
    private String modelVersion;
    private Instant assessedAt;

    public record Signal(String code, int score, String detail) {
    }

    public record Channel(String deviceId, String ipAddress, String userAgent, String countryCode) {
    }

    protected FraudAssessmentDocument() {
    }

    FraudAssessmentDocument(String id, String paymentId, String payerAccountId, String payeeAccountId,
                            Decimal128 amount, String currency, String paymentMethod, int riskScore, String decision,
                            List<Signal> signals, Channel channel, String modelVersion, Instant assessedAt) {
        this.id = id;
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.paymentId = paymentId;
        this.payerAccountId = payerAccountId;
        this.payeeAccountId = payeeAccountId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.riskScore = riskScore;
        this.decision = decision;
        this.signals = signals;
        this.channel = channel;
        this.modelVersion = modelVersion;
        this.assessedAt = assessedAt;
    }

    String getId() {
        return id;
    }

    int getSchemaVersion() {
        return schemaVersion;
    }

    String getPaymentId() {
        return paymentId;
    }

    String getPayerAccountId() {
        return payerAccountId;
    }

    String getPayeeAccountId() {
        return payeeAccountId;
    }

    Decimal128 getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    String getPaymentMethod() {
        return paymentMethod;
    }

    int getRiskScore() {
        return riskScore;
    }

    String getDecision() {
        return decision;
    }

    List<Signal> getSignals() {
        return signals;
    }

    Channel getChannel() {
        return channel;
    }

    String getModelVersion() {
        return modelVersion;
    }

    Instant getAssessedAt() {
        return assessedAt;
    }
}
