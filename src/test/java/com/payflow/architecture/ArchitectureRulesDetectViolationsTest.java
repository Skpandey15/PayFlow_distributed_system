package com.payflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards against vacuous architecture rules. A rule that "passes" because its package pattern matches
 * nothing is worse than no rule, so each key rule is run against deliberately violating fixture classes
 * and must fail.
 */
class ArchitectureRulesDetectViolationsTest {

    private final JavaClasses fixtures = new ClassFileImporter().importPackages("com.payflow.archfixture");

    @Test
    void frameworkDependencyInDomainIsDetected() {
        assertThatThrownBy(() -> ArchitectureTest.domain_is_framework_free.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyDomainObject");
    }

    @Test
    void floatingPointMoneyIsDetected() {
        assertThatThrownBy(() -> ArchitectureTest.no_binary_floating_point_fields.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyDomainObject.amount");
    }

    @Test
    void controllerUsingRepositoryIsDetected() {
        assertThatThrownBy(() -> ArchitectureTest.inbound_adapters_do_not_touch_persistence.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyController");
    }

    @Test
    void kafkaInTheDomainIsDetected() {
        assertThatThrownBy(() -> ArchitectureTest.core_does_not_know_messaging.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("KafkaAwareDomainObject");
    }

    @Test
    void kafkaListenerOutsideInboundMessagingAdapterIsDetected() {
        assertThatThrownBy(() -> ArchitectureTest.kafka_listeners_live_in_inbound_messaging_adapters.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PublishingController");
    }

    @Test
    void controllerPublishingToKafkaIsDetected() {
        assertThatThrownBy(() -> ArchitectureTest.web_controllers_never_publish_events.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PublishingController");
        assertThatThrownBy(() -> ArchitectureTest.only_the_platform_touches_the_kafka_producer.check(fixtures))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void publicSpringDataRepositoryIsDetected() {
        assertThatThrownBy(() -> ArchitectureTest.spring_data_repositories_live_in_persistence_adapters.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyJpaRepository");
    }
}
