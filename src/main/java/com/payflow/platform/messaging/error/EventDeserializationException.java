package com.payflow.platform.messaging.error;

/** The record value is not a readable PayFlow envelope (poison message). */
public class EventDeserializationException extends PermanentEventProcessingException {

    public EventDeserializationException(String message, Throwable cause) {
        super(FailureCategory.DESERIALIZATION, "EVENT_NOT_DESERIALIZABLE", message, cause);
    }
}
