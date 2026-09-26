package com.payflow.platform.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Captures the current W3C {@code traceparent} so it can be stored with an outbox row and restored on the
 * Kafka record. Without this, the trace would be broken at the outbox: the relay publishes from a scheduler
 * thread that has no knowledge of the HTTP request or consumer that recorded the event.
 */
@Component
public class TraceparentSupplier {

    private final ObjectProvider<Tracer> tracer;

    public TraceparentSupplier(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    public String current() {
        Tracer t = tracer.getIfAvailable();
        Span span = t == null ? null : t.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext c = span.context();
        return "00-" + c.traceId() + "-" + c.spanId() + "-" + (Boolean.TRUE.equals(c.sampled()) ? "01" : "00");
    }
}
