package com.payflow.platform.messaging.error;

/** Retrying cannot succeed: dead-letter immediately (excluded from retry topics). */
public class PermanentEventProcessingException extends EventProcessingException {

    public PermanentEventProcessingException(FailureCategory category, String errorCode, String message, Throwable cause) {
        super(category, errorCode, message, cause);
        if (category.retryable()) {
            throw new IllegalArgumentException(category + " is retryable");
        }
    }
}
