package com.payflow.support;

import com.payflow.railsim.RailSimulator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

/**
 * Real PostgreSQL, MongoDB and Kafka (KRaft) for integration tests. In-memory substitutes (H2, embedded or
 * mocked Kafka) would not reproduce what these tests exist to prove: PostgreSQL unique-index and row-lock
 * semantics, deferred triggers, and Kafka partitioning, offsets, redelivery and consumer-group rebalancing.
 * The containers are shared by every test class through Spring's context cache.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.1-alpine"));
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));
    }

    /**
     * The external settlement rails: the same simulator the lab runs as a container, here in-process on a random
     * port. PayFlow still reaches it over real HTTP, so timeouts, resets and slow answers are genuine network behaviour.
     */
    @Bean(destroyMethod = "stop")
    RailSimulator railSimulator() throws IOException {
        return new RailSimulator().start(0);
    }

    @Bean
    DynamicPropertyRegistrar railSimulatorUrl(RailSimulator rail) {
        return registry -> registry.add("payflow.settlement.rail.base-url", () -> "http://localhost:" + rail.port());
    }
}
