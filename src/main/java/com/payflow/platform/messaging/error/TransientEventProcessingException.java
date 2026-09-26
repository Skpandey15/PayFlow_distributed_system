package com.payflow.platform.messaging.error;

/** May succeed later: bounded non-blocking retries, then dead-letter. */
public class TransientEventProcessingException extends EventProcessingException {

    public TransientEventProcessingException(FailureCategory category, String errorCode, String message, Throwable cause) {
        super(category, errorCode, message, cause);
        if (!category.retryable()) {
            throw new IllegalArgumentException(category + " is not retryable");
        }
    }
}
