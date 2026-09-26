package com.payflow.platform.messaging;

/** Kafka record headers set by PayFlow producers. They allow routing, logging and triage without parsing payloads. */
public final class MessagingHeaders {

    public static final String EVENT_ID = "payflow-event-id";
    public static final String EVENT_TYPE = "payflow-event-type";
    public static final String EVENT_VERSION = "payflow-event-version";
    public static final String PRODUCER = "payflow-producer";
    public static final String CORRELATION_ID = "payflow-correlation-id";
    /** W3C Trace Context, captured when the event was recorded (not when the relay sent it). */
    public static final String TRACEPARENT = "traceparent";
    public static final String FAILURE_CATEGORY = "payflow-failure-category";
    public static final String ERROR_CODE = "payflow-error-code";
    public static final String FAILED_CONSUMER = "payflow-failed-consumer";
    public static final String REPLAYED_FROM = "payflow-replayed-from";

    private MessagingHeaders() {
    }
}
