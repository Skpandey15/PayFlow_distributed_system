package com.payflow.platform.messaging.error;

import com.payflow.contracts.ContractViolation;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.application.DependencyUnavailableException;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

/** The policy table of docs/architecture/EXCEPTION-ARCHITECTURE.md, as executable assertions. */
class FailureClassifierTest {

    static FailureCategory category(Throwable t) {
        return FailureClassifier.classify(t).category();
    }

    @Test
    void transientInfrastructureIsRetried() {
        assertThat(category(new DependencyUnavailableException("X", "down", null))).isEqualTo(FailureCategory.TRANSIENT_INFRASTRUCTURE);
        assertThat(category(new DataAccessResourceFailureException("db"))).isEqualTo(FailureCategory.TRANSIENT_INFRASTRUCTURE);
        assertThat(category(new QueryTimeoutException("slow"))).isEqualTo(FailureCategory.TRANSIENT_INFRASTRUCTURE);
        assertThat(category(new CannotCreateTransactionException("pool"))).isEqualTo(FailureCategory.TRANSIENT_INFRASTRUCTURE);
        assertThat(category(new RuntimeException(new ConnectException("refused")))).isEqualTo(FailureCategory.TRANSIENT_INFRASTRUCTURE);
        assertThat(FailureCategory.TRANSIENT_INFRASTRUCTURE.retryable()).isTrue();
    }

    @Test
    void concurrencyConflictsAreRetried() {
        assertThat(category(new ConcurrencyConflictException("stale", null))).isEqualTo(FailureCategory.CONCURRENCY);
        assertThat(category(new CannotAcquireLockException("deadlock"))).isEqualTo(FailureCategory.CONCURRENCY);
        assertThat(FailureCategory.CONCURRENCY.retryable()).isTrue();
    }

    @Test
    void businessAndContractFailuresAreNeverRetried() {
        assertThat(category(new DomainRuleViolationException("FUNDS_INSUFFICIENT", "x"))).isEqualTo(FailureCategory.BUSINESS_RULE);
        assertThat(category(new InvalidStateTransitionException("FUNDS_ALREADY_CAPTURED", "x"))).isEqualTo(FailureCategory.BUSINESS_RULE);
        assertThat(category(new NotFoundException("PAYMENT_SAGA_NOT_FOUND", "x"))).isEqualTo(FailureCategory.BUSINESS_RULE);
        assertThat(category(new ContractViolation("missing"))).isEqualTo(FailureCategory.CONTRACT_VIOLATION);
        assertThat(category(new DataIntegrityViolationException("ck"))).isEqualTo(FailureCategory.DATA_INTEGRITY);
        assertThat(FailureClassifier.classify(new DomainRuleViolationException("C", "x")))
                .isInstanceOf(PermanentEventProcessingException.class);
    }

    @Test
    void classificationSeesThroughContainerWrappingAndKeepsStableCodes() {
        EventProcessingException classified = FailureClassifier.classify(new ListenerExecutionFailedException("wrapped",
                new DomainRuleViolationException("FUNDS_INSUFFICIENT", "Insufficient funds on 1234")));
        assertThat(classified.category()).isEqualTo(FailureCategory.BUSINESS_RULE);
        assertThat(classified.errorCode()).as("code, never the data-bearing message").isEqualTo("FUNDS_INSUFFICIENT");

        UnsupportedEventVersionException already = new UnsupportedEventVersionException("X", 9);
        assertThat(FailureClassifier.classify(new RuntimeException(already))).isSameAs(already);
    }

    @Test
    void unknownFailuresGetBoundedRetriesThenTheDlt() {
        EventProcessingException classified = FailureClassifier.classify(new IllegalStateException("bug"));
        assertThat(classified.category()).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(classified).isInstanceOf(TransientEventProcessingException.class);
        assertThat(classified.errorCode()).isEqualTo("UNKNOWN:IllegalStateException");
    }
}
