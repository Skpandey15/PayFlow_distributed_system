package com.payflow.platform.messaging.error;

/** A known event type in a version this deployment cannot read. */
public class UnsupportedEventVersionException extends PermanentEventProcessingException {

    public UnsupportedEventVersionException(String eventType, int version) {
        super(FailureCategory.UNSUPPORTED_VERSION, "UNSUPPORTED_EVENT_VERSION",
                "Unsupported version " + version + " of " + eventType, null);
    }
}
