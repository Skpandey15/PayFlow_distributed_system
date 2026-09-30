package com.payflow.fraud.adapter.out.persistence;

import com.payflow.fraud.application.port.out.FraudAssessmentRepositoryPort;
import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.shared.domain.AccountId;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * MongoDB adapter for fraud assessments.
 *
 * <p>Failure semantics: connectivity and timeout failures become {@link FraudStoreUnavailableException}
 * (retryable, surfaced as 503). The Payment context then fails closed and leaves the payment CREATED.
 * Client timeouts are kept short (see application.yml) so an outage fails fast instead of tying up request threads.
 */
@Component
public class MongoFraudAssessmentAdapter implements FraudAssessmentRepositoryPort {

    private final MongoTemplate mongo;

    public MongoFraudAssessmentAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Creates the indexes this adapter relies on. The unique index on {@code paymentId} is what makes
     * "one assessment per payment" true under concurrency. Idempotent, safe to run on every start.
     */
    public void ensureIndexes() {
        var ops = mongo.indexOps(FraudAssessmentDocument.class);
        ops.createIndex(new Index().on("paymentId", Sort.Direction.ASC).unique().named("ux_payment_id"));
        ops.createIndex(new Index().on("payerAccountId", Sort.Direction.ASC).on("assessedAt", Sort.Direction.DESC)
                .named("ix_payer_assessed_at"));
    }

    @Override
    public void save(FraudAssessment assessment) {
        guard(() -> {
            try {
                mongo.insert(FraudAssessmentDocumentMapper.toDocument(assessment));
            } catch (DuplicateKeyException e) {
                throw new DuplicateAssessmentException(assessment.paymentId(), e);
            }
            return null;
        });
    }

    @Override
    public Optional<FraudAssessment> findByPaymentId(UUID paymentId) {
        return guard(() -> Optional.ofNullable(mongo.findOne(
                        Query.query(Criteria.where("paymentId").is(paymentId.toString())), FraudAssessmentDocument.class))
                .map(FraudAssessmentDocumentMapper::toDomain));
    }

    @Override
    public long countByPayerSince(AccountId payerAccountId, Instant since) {
        return guard(() -> mongo.count(Query.query(Criteria.where("payerAccountId").is(payerAccountId.toString())
                .and("assessedAt").gte(since)), FraudAssessmentDocument.class));
    }

    private static <T> T guard(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
            throw new FraudStoreUnavailableException(e);
        }
    }
}
