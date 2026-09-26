package com.payflow.archfixture.adapter.in.web;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Deliberate violation fixture: a controller that publishes to Kafka directly and hosts a listener.
 * (Not a Spring bean, so the listener is never registered.)
 */
public class PublishingController {

    private final KafkaTemplate<String, String> kafka;

    public PublishingController(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    public void pay() {
        kafka.send("payment.events", "payment created");
    }

    @KafkaListener(topics = "payment.events")
    void listen(String message) {
    }
}
