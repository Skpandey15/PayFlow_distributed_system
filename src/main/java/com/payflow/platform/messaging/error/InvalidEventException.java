package com.payflow.platform.messaging.error;

/** The envelope or payload violates its contract (missing mandatory data, unknown type, key mismatch). */
public class InvalidEventException extends PermanentEventProcessingException {

    public InvalidEventException(String errorCode, String message, Throwable cause) {
        super(FailureCategory.CONTRACT_VIOLATION, errorCode, message, cause);
    }
}
