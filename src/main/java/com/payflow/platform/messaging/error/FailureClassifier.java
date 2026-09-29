package com.payflow.platform.messaging.error;

import com.payflow.contracts.ContractViolation;
import com.payflow.shared.application.ApplicationException;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.application.DependencyUnavailableException;
import com.payflow.shared.domain.DomainException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

/**
 * Maps any failure raised while processing an event onto a {@link FailureCategory}, and from there onto
 * retry or dead-letter. This mapping is the single place where technical exceptions become policy.
 */
public final class FailureClassifier {

    private FailureClassifier() {
    }

    public static EventProcessingException classify(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof EventProcessingException already) {
                return already;
            }
        }
        FailureCategory category = categoryOf(failure);
        String code = errorCode(failure, category);
        String message = category + " while processing event: " + code;
        return category.retryable()
                ? new TransientEventProcessingException(category, code, message, failure)
                : new PermanentEventProcessingException(category, code, message, failure);
    }

    /** Also used by the HTTP boundary, so synchronous and asynchronous failures share one taxonomy. */
    public static FailureCategory categoryOf(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ContractViolation) {
                return FailureCategory.CONTRACT_VIOLATION;
            }
            if (t instanceof ConcurrencyConflictException || t instanceof OptimisticLockingFailureException
                    || t instanceof PessimisticLockingFailureException) {
                return FailureCategory.CONCURRENCY;
            }
            if (t instanceof DependencyUnavailableException || t instanceof TransientDataAccessException
                    || t instanceof DataAccessResourceFailureException || t instanceof CannotCreateTransactionException
                    || t instanceof TransactionTimedOutException || t instanceof ConnectException
                    || t instanceof SocketTimeoutException || t instanceof SQLTransientException
                    || t instanceof SQLRecoverableException) {
                return FailureCategory.TRANSIENT_INFRASTRUCTURE;
            }
            if (t instanceof DomainException || t instanceof ApplicationException) {
                return FailureCategory.BUSINESS_RULE;
            }
            if (t instanceof DataIntegrityViolationException) {
                return FailureCategory.DATA_INTEGRITY;
            }
        }
        return FailureCategory.UNKNOWN;
    }

    /** Stable code without data values: domain/application codes where available, else the exception type. */
    private static String errorCode(Throwable failure, FailureCategory category) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof DomainException d) {
                return d.code();
            }
            if (t instanceof ApplicationException a) {
                return a.code();
            }
        }
        return category.name() + ":" + failure.getClass().getSimpleName();
    }
}
