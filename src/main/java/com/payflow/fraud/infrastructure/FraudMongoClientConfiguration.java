package com.payflow.fraud.infrastructure;

import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB client policy for the fraud store.
 *
 * <ul>
 *   <li><b>Fail fast</b>: the driver defaults (30s server selection) would park request threads for
 *       30 seconds during an outage. Short timeouts turn an outage into a quick, retryable 503.</li>
 *   <li><b>Write concern majority</b>: an acknowledged assessment survives primary failover, so a decision
 *       cannot silently disappear and be re-scored differently on retry.</li>
 *   <li><b>Read concern majority</b>: reads never observe writes that could still be rolled back.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class FraudMongoClientConfiguration {

    @Bean
    MongoClientSettingsBuilderCustomizer fraudMongoClientPolicy(
            @Value("${payflow.fraud.mongo.server-selection-timeout:2s}") Duration serverSelectionTimeout,
            @Value("${payflow.fraud.mongo.connect-timeout:2s}") Duration connectTimeout,
            @Value("${payflow.fraud.mongo.read-timeout:3s}") Duration readTimeout) {
        return builder -> builder
                .writeConcern(WriteConcern.MAJORITY.withWTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                .readConcern(ReadConcern.MAJORITY)
                .applyToClusterSettings(c -> c.serverSelectionTimeout(serverSelectionTimeout.toMillis(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(s -> s
                        .connectTimeout((int) connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout((int) readTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }
}
