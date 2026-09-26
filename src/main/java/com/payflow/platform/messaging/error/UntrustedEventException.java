package com.payflow.platform.messaging.error;

/**
 * The message claims a producer or topic that is not allowed for its type. Kafka authentication tells us
 * who connected; this check tells us whether the message is one that identity may send. It is a security signal
 * and is always dead-lettered and alerted, never retried.
 */
public class UntrustedEventException extends PermanentEventProcessingException {

    public UntrustedEventException(String errorCode, String message) {
        super(FailureCategory.UNTRUSTED_SOURCE, errorCode, message, null);
    }
}
