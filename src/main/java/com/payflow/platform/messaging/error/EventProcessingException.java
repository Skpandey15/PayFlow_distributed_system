package com.payflow.platform.messaging.error;

/**
 * Base of the asynchronous failure taxonomy. Consumers never throw raw technical exceptions to the Kafka
 * container: the consumer boundary translates everything into a classified subclass, so the retry topology
 * can route on type alone ({@link PermanentEventProcessingException} goes straight to the DLT).
 */
public abstract class EventProcessingException extends RuntimeException {

    private final FailureCategory category;
    private final String errorCode;

    protected EventProcessingException(FailureCategory category, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.errorCode = errorCode;
    }

    public FailureCategory category() {
        return category;
    }

    /** Stable, sanitised code (safe to put in DLT headers and metrics). Never contains data values. */
    public String errorCode() {
        return errorCode;
    }
}
