package com.payflow.contracts;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.payflow.contracts.EventCatalog.EventDefinition;
import com.payflow.contracts.fraud.FraudMessages.AssessPaymentRiskV1;
import com.payflow.contracts.fraud.FraudMessages.RiskAssessedV1;
import com.payflow.contracts.fraud.FraudMessages.RiskAssessedV2;
import com.payflow.contracts.funds.FundsMessages.CaptureFundsV1;
import com.payflow.contracts.funds.FundsMessages.FundsCapturedV1;
import com.payflow.contracts.funds.FundsMessages.FundsDepositedV1;
import com.payflow.contracts.funds.FundsMessages.FundsReleasedV1;
import com.payflow.contracts.funds.FundsMessages.FundsReservationFailedV1;
import com.payflow.contracts.funds.FundsMessages.FundsReservedV1;
import com.payflow.contracts.funds.FundsMessages.ReleaseFundsV1;
import com.payflow.contracts.funds.FundsMessages.ReserveFundsV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentAuthorizedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentCancelledV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentCreatedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentFailedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentProcessingStartedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentRejectedV1;
import com.payflow.contracts.payment.PaymentEvents.PaymentSettledV1;
import com.payflow.contracts.settlement.SettlementMessages.SettlementCompletedV1;
import com.payflow.contracts.settlement.SettlementMessages.SettlementDeclinedV1;
import com.payflow.contracts.settlement.SettlementMessages.SubmitSettlementV1;
import com.payflow.platform.messaging.EventCodec;
import com.payflow.platform.messaging.EventEnvelope;
import com.payflow.platform.messaging.error.InvalidEventException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract governance as code (the build-time part of what a Schema Registry does; see ADR-011 and ADR-016):
 * every message type and version has a checked-in JSON Schema, producers' output validates strictly against it
 * (a silently mutated record fails the build), successive versions are backward compatible, and consumers upcast
 * old versions and tolerate unknown fields.
 */
class EventContractTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final SchemaRegistry SCHEMAS = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    static final String P = UUID.randomUUID().toString();
    static final String A = UUID.randomUUID().toString();
    static final String B = UUID.randomUUID().toString();

    /** A representative instance of every current contract version (what producers emit). */
    static Stream<Object> currentPayloads() {
        return Stream.of(
                new PaymentCreatedV1(P, A, B, "125.50", "USD", "CARD"), new PaymentAuthorizedV1(P),
                new PaymentProcessingStartedV1(P), new PaymentRejectedV1(P, "RISK_DECLINED"), new PaymentCancelledV1(P),
                new PaymentSettledV1(P, "125.50", "USD"), new PaymentFailedV1(P, "SETTLEMENT_DECLINED:X"),
                new AssessPaymentRiskV1(P, A, B, "10.00", "USD", "CARD", "dev", "203.0.113.1", "ua", "US"),
                new RiskAssessedV2(P, false, 90, "RISK_DECLINED:HIGH_AMOUNT", List.of("HIGH_AMOUNT"), "rules-v1"),
                new ReserveFundsV1(P, A, B, "10.00", "USD"), new CaptureFundsV1(P),
                new ReleaseFundsV1(P, A, "10.00", "USD", "CANCELLED"),
                new FundsReservedV1(P, UUID.randomUUID().toString(), A, "10.00", "USD"),
                new FundsReservationFailedV1(P, "INSUFFICIENT_FUNDS"), new FundsCapturedV1(P, A, B, "10.00", "USD"),
                new FundsReleasedV1(P, "10.00", "USD", "CANCELLED"),
                new FundsDepositedV1(UUID.randomUUID().toString(), A, "100.00", "USD"),
                new SubmitSettlementV1(P, "CARD", "10.00", "USD", null),
                new SettlementCompletedV1(P, UUID.randomUUID().toString(), "CARD-1"),
                new SettlementDeclinedV1(P, UUID.randomUUID().toString(), "SIMULATED_DECLINE"));
    }

    static JsonNode schemaNode(String type, int version) {
        try (InputStream in = EventContractTest.class.getResourceAsStream("/contracts/" + type + ".v" + version + ".schema.json")) {
            assertThat(in).as("schema file for %s v%d", type, version).isNotNull();
            return JSON.readTree(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Schema schema(String type, int version) {
        return SCHEMAS.getSchema(JSON.writeValueAsString(schemaNode(type, version)));
    }

    @Test
    void everyCatalogVersionHasASchemaWithMatchingTopicAndOwner() {
        for (EventDefinition d : EventCatalog.all().values()) {
            for (int version : d.versions().keySet()) {
                JsonNode s = schemaNode(d.eventType(), version);
                assertThat(s.get("x-topic").asString()).as(d.eventType()).isEqualTo(d.topic());
                assertThat(s.get("x-producer").asString()).as(d.eventType()).isEqualTo(d.producer());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("currentPayloads")
    void producerOutputValidatesStrictlyAgainstItsSchema(Object payload) {
        EventDefinition d = EventCatalog.forPayload(payload.getClass());
        JsonNode instance = JSON.valueToTree(payload);
        assertThat(schema(d.eventType(), d.currentVersion()).validate(instance))
                .as("%s v%d must match its checked-in schema exactly (no silent mutation)", d.eventType(), d.currentVersion())
                .isEmpty();
    }

    @Test
    void envelopesValidateAgainstTheEnvelopeSchema() {
        EventCodec codec = new EventCodec(JSON);
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "FundsCaptured", 1, Producers.ACCOUNT, "Payment", P,
                Instant.now(), "corr-1", UUID.randomUUID(), UUID.randomUUID(),
                codec.toTree(new FundsCapturedV1(P, A, B, "1.00", "USD")));
        JsonNode encoded = JSON.readTree(codec.encode(envelope));
        assertThat(SCHEMAS.getSchema(JSON.writeValueAsString(schemaNode("Envelope", 1))).validate(encoded)).isEmpty();
        assertThat(codec.decode(codec.encode(envelope))).isEqualTo(envelope);
    }

    @Test
    void successiveVersionsAreBackwardCompatible() {
        for (EventDefinition d : EventCatalog.all().values()) {
            List<Integer> versions = d.versions().keySet().stream().sorted().toList();
            for (int i = 1; i < versions.size(); i++) {
                assertThat(compatibilityViolations(schemaNode(d.eventType(), versions.get(i - 1)),
                        schemaNode(d.eventType(), versions.get(i))))
                        .as("%s v%d → v%d", d.eventType(), versions.get(i - 1), versions.get(i)).isEmpty();
            }
        }
    }

    @Test
    void compatibilityCheckDetectsBreakingChanges() {
        ObjectNode v1 = (ObjectNode) schemaNode("RiskAssessed", 1);
        ObjectNode removedField = v1.deepCopy();
        ((ObjectNode) removedField.get("properties")).remove("riskScore");
        ObjectNode newRequired = (ObjectNode) schemaNode("RiskAssessed", 2).deepCopy();
        newRequired.withArray("required").add("modelVersion");
        ObjectNode typeChange = v1.deepCopy();
        ((ObjectNode) typeChange.get("properties")).set("riskScore", JSON.readTree("{\"type\":\"string\"}"));

        assertThat(compatibilityViolations(v1, removedField)).anyMatch(v -> v.contains("removed"));
        assertThat(compatibilityViolations(v1, newRequired)).anyMatch(v -> v.contains("newly required"));
        assertThat(compatibilityViolations(v1, typeChange)).anyMatch(v -> v.contains("type changed"));
    }

    /**
     * BACKWARD_TRANSITIVE-style rule set (what a Schema Registry would enforce): a new version may only add optional
     * properties. No property may be removed, no type may change, nothing may become required.
     */
    static List<String> compatibilityViolations(JsonNode older, JsonNode newer) {
        List<String> violations = new ArrayList<>();
        JsonNode oldProps = older.get("properties");
        JsonNode newProps = newer.get("properties");
        for (String field : oldProps.propertyNames()) {
            if (!newProps.has(field)) {
                violations.add(field + " removed");
            } else if (!oldProps.get(field).path("type").equals(newProps.get(field).path("type"))) {
                violations.add(field + " type changed");
            }
        }
        Set<String> oldRequired = new HashSet<>();
        older.get("required").forEach(n -> oldRequired.add(n.asString()));
        newer.get("required").forEach(n -> {
            if (!oldRequired.contains(n.asString())) {
                violations.add(n.asString() + " newly required");
            }
        });
        return violations;
    }

    @Test
    void consumersUpcastOldVersionsToTheCurrentShape() {
        EventCodec codec = new EventCodec(JSON);
        EventDefinition riskAssessed = EventCatalog.get("RiskAssessed");
        EventEnvelope v1 = new EventEnvelope(UUID.randomUUID(), "RiskAssessed", 1, Producers.FRAUD, "Payment", P,
                Instant.now(), "c", null, null, codec.toTree(new RiskAssessedV1(P, true, 12, null)));

        Object upcast = codec.payload(v1, riskAssessed);

        assertThat(upcast).isEqualTo(new RiskAssessedV2(P, true, 12, null, List.of(), "unknown"));
    }

    @Test
    void oldConsumersTolerateNewOptionalFields() {
        EventCodec codec = new EventCodec(JSON);
        JsonNode v2Payload = codec.toTree(new RiskAssessedV2(P, true, 5, null, List.of("MISSING_DEVICE"), "rules-v2"));
        EventEnvelope readAsV1 = new EventEnvelope(UUID.randomUUID(), "RiskAssessed", 1, Producers.FRAUD, "Payment", P,
                Instant.now(), "c", null, null, v2Payload);
        EventDefinition v1Only = new EventDefinition("RiskAssessed", Topics.FRAUD_EVENTS, Producers.FRAUD,
                Map.of(1, RiskAssessedV1.class), Map.of());

        assertThat(codec.payload(readAsV1, v1Only)).isEqualTo(new RiskAssessedV1(P, true, 5, null));
    }

    @Test
    void missingMandatoryFieldIsAContractViolation() {
        EventCodec codec = new EventCodec(JSON);
        ObjectNode incomplete = JSON.createObjectNode().put("paymentId", P).put("reason", (String) null);
        EventEnvelope e = new EventEnvelope(UUID.randomUUID(), "FundsReservationFailed", 1, Producers.ACCOUNT, "Payment",
                P, Instant.now(), "c", null, null, incomplete);

        assertThatThrownBy(() -> codec.payload(e, EventCatalog.get("FundsReservationFailed")))
                .isInstanceOf(InvalidEventException.class)
                .extracting("errorCode").isEqualTo("PAYLOAD_CONTRACT_VIOLATION");
    }

    @Test
    void producersCannotEmitTypesTheyDoNotOwn() {
        EventDefinition d = EventCatalog.forPayload(FundsReservedV1.class);
        assertThat(d.producer()).isEqualTo(Producers.ACCOUNT);
        assertThat(d.topic()).isEqualTo(Topics.FUNDS_EVENTS);
    }
}
