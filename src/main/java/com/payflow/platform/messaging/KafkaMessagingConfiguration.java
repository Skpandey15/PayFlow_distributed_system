package com.payflow.platform.messaging;

import com.payflow.contracts.Topics;
import com.payflow.platform.messaging.error.EventProcessingException;
import com.payflow.platform.messaging.error.PermanentEventProcessingException;
import com.payflow.platform.messaging.outbox.OutboxProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.retrytopic.DeadLetterPublishingRecovererFactory;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Event-backbone wiring:
 * <ul>
 *   <li>Main topics are declared explicitly (partitions, replication, retention), not auto-created by accident.</li>
 *   <li>Retry topology (per listener via {@code @RetryableTopic}): {@link PermanentEventProcessingException} is
 *       fatal and goes straight to the DLT; everything else gets bounded, delayed, non-blocking retries.</li>
 *   <li>DLT records are <b>sanitised</b>: no stack traces or exception messages (they can contain data values),
 *       but a failure category, stable error code and the failed consumer group are added.</li>
 *   <li>Listener failures are logged once by {@code EventConsumerSupport}; the container's own error logging
 *       is lowered to DEBUG to avoid duplicate ERROR records for one failure.</li>
 * </ul>
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(OutboxProperties.class)
public class KafkaMessagingConfiguration extends RetryTopicConfigurationSupport {

    @Override
    protected void manageNonBlockingFatalExceptions(List<Class<? extends Throwable>> nonBlockingFatalExceptions) {
        nonBlockingFatalExceptions.add(PermanentEventProcessingException.class);
    }

    @Override
    protected Consumer<DeadLetterPublishingRecovererFactory> configureDeadLetterPublishingContainerFactory() {
        return factory -> {
            factory.neverLogListenerException();
            factory.setDeadLetterPublishingRecovererCustomizer(recoverer -> {
                recoverer.excludeHeader(HeadersToAdd.EX_STACKTRACE, HeadersToAdd.EX_MSG);
                recoverer.addHeadersFunction(KafkaMessagingConfiguration::sanitizedFailureHeaders);
            });
        };
    }

    @Override
    protected void configureCustomizers(CustomizersConfigurer customizersConfigurer) {
        customizersConfigurer.customizeErrorHandler(handler -> handler.setLogLevel(KafkaException.Level.DEBUG));
    }

    static Headers sanitizedFailureHeaders(ConsumerRecord<?, ?> record, Exception failure) {
        RecordHeaders headers = new RecordHeaders();
        EventProcessingException classified = null;
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof EventProcessingException e) {
                classified = e;
                break;
            }
        }
        String category = classified == null ? "UNKNOWN" : classified.category().name();
        String code = classified == null ? "UNCLASSIFIED" : classified.errorCode();
        headers.add(MessagingHeaders.FAILURE_CATEGORY, category.getBytes(StandardCharsets.UTF_8));
        headers.add(MessagingHeaders.ERROR_CODE, code.getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    @Bean
    KafkaAdmin.NewTopics payflowTopics(@Value("${payflow.messaging.topic.partitions:6}") int partitions,
                                      @Value("${payflow.messaging.topic.replication-factor:1}") int replicationFactor,
                                      @Value("${payflow.messaging.topic.retention:7d}") Duration retention) {
        return new KafkaAdmin.NewTopics(
                topic(Topics.PAYMENT_EVENTS, partitions, replicationFactor, retention),
                topic(Topics.FRAUD_COMMANDS, partitions, replicationFactor, retention),
                topic(Topics.FRAUD_EVENTS, partitions, replicationFactor, retention),
                topic(Topics.FUNDS_COMMANDS, partitions, replicationFactor, retention),
                topic(Topics.FUNDS_EVENTS, partitions, replicationFactor, retention),
                topic(Topics.SETTLEMENT_COMMANDS, partitions, replicationFactor, retention),
                topic(Topics.SETTLEMENT_EVENTS, partitions, replicationFactor, retention));
    }

    private static NewTopic topic(String name, int partitions, int replicationFactor, Duration retention) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", Long.toString(retention.toMillis()))
                .config("min.insync.replicas", Integer.toString(Math.max(1, Math.min(2, replicationFactor - 1))))
                .build();
    }
}
