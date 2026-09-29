package com.payflow.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.Repository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable architecture: every rule here fails the build when violated.
 *
 * <p>Dependency direction (Clean Architecture / Ports and Adapters):
 * <pre>
 *   infrastructure (wiring) -> adapter -> application (ports, use cases) -> domain
 * </pre>
 * plus bounded-context isolation: a context may reach another context only through that context's
 * published inbound ports ({@code application.port.in}), and only from its own outbound adapters.
 */
@AnalyzeClasses(packages = "com.payflow", importOptions = {ImportOption.DoNotIncludeTests.class,
        ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

    static final List<String> CONTEXTS = List.of("payment", "account", "fraud", "ledger", "settlement", "reconciliation");

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "jakarta.persistence..", "org.hibernate..", "org.bson..", "com.mongodb..",
            "jakarta.servlet..", "jakarta.validation..", "tools.jackson..", "com.fasterxml.jackson..",
            "io.swagger..", "org.slf4j..", "org.apache.kafka..", "io.github.resilience4j..",
            "io.lettuce..", "redis.clients..", "io.confluent..", "io.micrometer..", "io.opentelemetry..",
            "io.prometheus.."};

    // ---------------------------------------------------------------- Clean Architecture layering

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("com.payflow..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .because("business rules must not change when Spring, JPA, MongoDB or HTTP change");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.payflow..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.payflow..application..", "com.payflow..adapter..", "com.payflow..infrastructure..",
                    "com.payflow.platform..")
            .because("dependencies point inward: the domain knows nothing about use cases or adapters");

    @ArchTest
    static final ArchRule application_is_framework_free = noClasses()
            .that().resideInAPackage("com.payflow..application..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .because("use cases orchestrate through ports; transaction boundaries use the TransactionRunner port, "
                    + "not @Transactional");

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters_or_infrastructure = noClasses()
            .that().resideInAPackage("com.payflow..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.payflow..adapter..", "com.payflow..infrastructure..", "com.payflow.platform..")
            .because("the application layer defines ports; adapters implement them (Dependency Inversion)");

    @ArchTest
    static final ArchRule onion_architecture_per_context = CompositeArchRule.of(
            CONTEXTS.stream().map(ArchitectureTest::onionFor).toList());

    private static ArchRule onionFor(String context) {
        String root = "com.payflow." + context;
        return onionArchitecture()
                .domainModels(root + ".domain..")
                .applicationServices(root + ".application..")
                .adapter("inbound", root + ".adapter.in..")
                .adapter("outbound", root + ".adapter.out..")
                .withOptionalLayers(true)
                // The composition root (infrastructure) wires every layer by design, and calls from other
                // contexts are governed by the dedicated cross-context rules below.
                .ignoreDependency(resideInAPackage(root + ".infrastructure.."), DescribedPredicate.alwaysTrue())
                .ignoreDependency(DescribedPredicate.not(resideInAPackage(root + "..")), DescribedPredicate.alwaysTrue())
                .as("Onion architecture of the " + context + " context");
    }

    // ---------------------------------------------------------------- Adapters

    @ArchTest
    static final ArchRule inbound_adapters_do_not_touch_persistence = noClasses()
            .that().resideInAPackage("com.payflow..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.payflow..adapter.out..", "org.springframework.data..", "jakarta.persistence..",
                    "org.springframework.jdbc..")
            .because("controllers call use cases, never repositories");

    @ArchTest
    static final ArchRule inbound_adapters_depend_on_ports_not_use_case_implementations = noClasses()
            .that().resideInAPackage("com.payflow..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("com.payflow..application.usecase..")
            .because("controllers depend on inbound port interfaces (Dependency Inversion)");

    @ArchTest
    static final ArchRule controllers_live_in_web_adapters = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("com.payflow..adapter.in.web..");

    @ArchTest
    static final ArchRule jpa_entities_live_in_persistence_adapters = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("com.payflow..adapter.out.persistence..");

    @ArchTest
    static final ArchRule mongo_documents_live_in_persistence_adapters = classes()
            .that().areAnnotatedWith(Document.class)
            .should().resideInAPackage("com.payflow..adapter.out.persistence..");

    @ArchTest
    static final ArchRule spring_data_repositories_live_in_persistence_adapters = classes()
            .that().areAssignableTo(Repository.class)
            .should().resideInAPackage("com.payflow..adapter.out.persistence..")
            .andShould().notBePublic()
            .because("framework repositories are an implementation detail behind the repository port");

    @ArchTest
    static final ArchRule persistence_models_never_leave_the_persistence_adapter = noClasses()
            .that().resideOutsideOfPackage("com.payflow..adapter.out.persistence..")
            .should().dependOnClassesThat(annotatedWithAny())
            .because("JPA entities / Mongo documents must never be exposed through APIs or used by use cases");

    private static DescribedPredicate<JavaClass> annotatedWithAny() {
        return DescribedPredicate.describe("are persistence models (@Entity or @Document)",
                c -> c.isAnnotatedWith(Entity.class) || c.isAnnotatedWith(Document.class));
    }

    // ---------------------------------------------------------------- Bounded-context isolation

    @ArchTest
    static final ArchRule contexts_only_use_each_others_published_ports = CompositeArchRule.of(crossContextRules());

    private static List<ArchRule> crossContextRules() {
        List<ArchRule> rules = new ArrayList<>();
        for (String from : CONTEXTS) {
            for (String to : CONTEXTS) {
                if (from.equals(to)) {
                    continue;
                }
                String target = "com.payflow." + to;
                rules.add(noClasses()
                        .that().resideInAPackage("com.payflow." + from + "..")
                        .should().dependOnClassesThat(resideInAPackage(target + "..")
                                .and(DescribedPredicate.not(resideInAPackage(target + ".application.port.in..")))
                                .as("internals of the " + to + " context"))
                        .as(from + " must not reach into the internals of " + to));
                rules.add(noClasses()
                        .that().resideInAPackage("com.payflow." + from + "..")
                        .and().resideOutsideOfPackage("com.payflow." + from + ".adapter.out..")
                        .should().dependOnClassesThat().resideInAPackage(target + "..")
                        .as(from + " may call " + to + " only from an outbound adapter (anti-corruption layer)"));
            }
        }
        return rules;
    }

    @ArchTest
    static final ArchRule shared_kernel_depends_on_no_context = noClasses()
            .that().resideInAPackage("com.payflow.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTEXTS.stream().map(c -> "com.payflow." + c + "..").toArray(String[]::new))
            .because("the shared kernel is the bottom of the dependency graph");

    @ArchTest
    static final ArchRule platform_depends_on_no_context = noClasses()
            .that().resideInAPackage("com.payflow.platform..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTEXTS.stream().map(c -> "com.payflow." + c + "..").toArray(String[]::new))
            .because("cross-cutting infrastructure must stay reusable when contexts are extracted");

    @ArchTest
    static final ArchRule contexts_are_free_of_cycles = slices()
            .matching("com.payflow.(*)..")
            .should().beFreeOfCycles();

    // ---------------------------------------------------------------- Event backbone (WP-02)

    @ArchTest
    static final ArchRule core_does_not_know_messaging = noClasses()
            .that().resideInAnyPackage("com.payflow..domain..", "com.payflow..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.payflow.contracts..", "com.payflow.platform.messaging..", "org.springframework.kafka..",
                    "org.apache.kafka..")
            .because("Kafka, envelopes, offsets, retry topics and wire contracts are adapter concerns; use cases talk "
                    + "to ports (PaymentEventPublisherPort, SagaCommandPort, ...)");

    @ArchTest
    static final ArchRule kafka_listeners_live_in_inbound_messaging_adapters = methods()
            .that().areAnnotatedWith(KafkaListener.class)
            .should().beDeclaredInClassesThat().resideInAPackage("com.payflow..adapter.in.messaging..")
            .because("consuming is an inbound adapter: it must translate, then call an application port");

    @ArchTest
    static final ArchRule only_the_platform_touches_the_kafka_producer = noClasses()
            .that().resideOutsideOfPackage("com.payflow.platform.messaging..")
            .should().dependOnClassesThat().areAssignableTo(KafkaTemplate.class)
            .because("contexts publish through the outbox (OutboxWriter) or DirectEventPublisher, never ad hoc");

    @ArchTest
    static final ArchRule web_controllers_never_publish_events = noClasses()
            .that().resideInAPackage("com.payflow..adapter.in.web..")
            .and().resideOutsideOfPackage("com.payflow.platform..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.kafka..", "org.apache.kafka..", "com.payflow.platform.messaging..",
                    "com.payflow.contracts..")
            .because("an HTTP request changes state through a use case; events are a consequence, written to the outbox");

    @ArchTest
    static final ArchRule contracts_are_plain_java = classes()
            .that().resideInAPackage("com.payflow.contracts..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.payflow.contracts..")
            .because("the published language must not leak JPA entities, domain types or frameworks");

    @ArchTest
    static final ArchRule outbound_messaging_adapters_use_published_contracts = noClasses()
            .that().resideInAPackage("com.payflow..adapter.out.messaging..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("events are integration contracts, never serialized persistence entities");

    // ---------------------------------------------------------------- Money and coding standards

    @ArchTest
    static final ArchRule no_binary_floating_point_fields = noFields()
            // Tuning knobs (breaker thresholds, backoff multipliers) are ratios, not money: composition-root
            // configuration is exempt, everything that can carry an amount is not.
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("com.payflow..infrastructure..")
            .should().haveRawType(double.class).orShould().haveRawType(float.class)
            .orShould().haveRawType(Double.class).orShould().haveRawType(Float.class)
            .because("money is BigDecimal; binary floating point cannot represent 0.10 exactly");

    @ArchTest
    static final ArchRule no_big_decimal_from_double = noClasses()
            .should().callConstructor(BigDecimal.class, double.class)
            .orShould().callMethod(BigDecimal.class, "valueOf", double.class)
            .because("new BigDecimal(0.1) is 0.1000000000000000055511151231257827...");

    // ---------------------------------------------------------------- WP-03 resilience and observability boundaries

    @ArchTest
    static final ArchRule resilience_lives_only_at_outbound_boundaries = noClasses()
            .that().resideOutsideOfPackages("com.payflow..adapter.out..", "com.payflow..infrastructure..",
                    "com.payflow.platform.web..")
            .should().dependOnClassesThat().resideInAPackage("io.github.resilience4j..")
            .because("circuit breakers, retries and bulkheads protect remote calls; domain, use cases, repositories "
                    + "and Kafka consumers must not be wrapped (Kafka has its own retry/DLT topology)");

    @ArchTest
    static final ArchRule telemetry_sdks_stay_out_of_the_core = noClasses()
            .that().resideInAnyPackage("com.payflow..domain..", "com.payflow..application..", "com.payflow.contracts..")
            .should().dependOnClassesThat().resideInAnyPackage("io.opentelemetry..", "io.prometheus..",
                    "io.micrometer..")
            .because("metrics and traces are recorded at adapter boundaries; business code stays instrument-free");

    @ArchTest
    static final ArchRule inbound_adapters_never_use_outbound_ports = noClasses()
            .that().resideInAPackage("com.payflow..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("com.payflow..application.port.out..")
            .because("operations and API controllers act only through use cases (authorisation, audit, "
                    + "idempotency live there), never directly on repositories or gateways");

    @ArchTest
    static final ArchRule no_field_injection = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("constructor injection keeps dependencies explicit, immutable and testable");

    @ArchTest
    static final ArchRule persistence_fields_are_not_public = fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .should().notBePublic();
}
