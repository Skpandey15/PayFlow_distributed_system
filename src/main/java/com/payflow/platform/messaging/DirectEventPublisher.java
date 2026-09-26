package com.payflow.platform.messaging;

import com.payflow.shared.application.DependencyUnavailableException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Synchronous publish (wait for acks=all) for <b>consume-process-produce</b> handlers whose state is not in
 * PostgreSQL (Fraud/MongoDB). Loss-free without an outbox because the <em>input</em> offset is committed only
 * after this send succeeds: a crash between the store write and the send redelivers the input, the handler
 * re-derives the same (idempotent) result, and it is published again. Consumers deduplicate.
 */
@Component
public class DirectEventPublisher {

    private final EnvelopeFactory envelopes;
    private final KafkaTemplate<String, String> kafka;

    public DirectEventPublisher(EnvelopeFactory envelopes, KafkaTemplate<String, String> kafka) {
        this.envelopes = envelopes;
        this.kafka = kafka;
    }

    public void publish(String producer, OutgoingMessage message) {
        PreparedMessage prepared = envelopes.prepare(producer, message);
        try {
            kafka.send(prepared.toRecord()).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("EVENT_PUBLISH_INTERRUPTED", "Interrupted while publishing", e);
        } catch (Exception e) {
            throw new DependencyUnavailableException("EVENT_PUBLISH_FAILED",
                    "Could not publish " + prepared.envelope().eventType(), e);
        }
    }
}
