package com.payflow.archfixture.domain;

import org.apache.kafka.clients.producer.ProducerRecord;

/** Deliberate violation fixture: a domain object that builds Kafka records. */
public class KafkaAwareDomainObject {

    ProducerRecord<String, String> toRecord() {
        return new ProducerRecord<>("payment.events", "key", "value");
    }
}
