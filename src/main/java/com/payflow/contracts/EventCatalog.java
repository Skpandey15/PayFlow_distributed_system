package com.payflow.contracts;

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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of every message type on the backbone: which topic it belongs to, who may produce it, which
 * versions exist, and how older versions are upcast to the current one.
 *
 * <p>Consumers use it to treat input as untrusted: an unknown type, a version outside the supported range,
 * the wrong topic or the wrong producer is rejected <em>before</em> any business logic runs.
 */
public final class EventCatalog {

    /** One message type. {@code versions} maps version → payload record; {@code upcasters} maps vN → vN+1. */
    public record EventDefinition(String eventType, String topic, String producer, Map<Integer, Class<?>> versions,
                                  Map<Integer, Function<Object, Object>> upcasters) {

        public int currentVersion() {
            return versions.keySet().stream().max(Integer::compare).orElseThrow();
        }

        public Class<?> payloadType(int version) {
            return versions.get(version);
        }

        public boolean supports(int version) {
            return versions.containsKey(version);
        }

        /** Upcasts a decoded payload of {@code fromVersion} to the current version (identity if already current). */
        public Object upcast(Object payload, int fromVersion) {
            Object current = payload;
            for (int v = fromVersion; v < currentVersion(); v++) {
                Function<Object, Object> step = upcasters.get(v);
                if (step == null) {
                    throw new IllegalStateException("No upcaster for " + eventType + " v" + v);
                }
                current = step.apply(current);
            }
            return current;
        }
    }

    private static final Map<String, EventDefinition> DEFINITIONS = List.of(
            single("PaymentCreated", Topics.PAYMENT_EVENTS, Producers.PAYMENT, PaymentCreatedV1.class),
            single("PaymentAuthorized", Topics.PAYMENT_EVENTS, Producers.PAYMENT, PaymentAuthorizedV1.class),
            single("PaymentProcessingStarted", Topics.PAYMENT_EVENTS, Producers.PAYMENT, PaymentProcessingStartedV1.class),
            single("PaymentRejected", Topics.PAYMENT_EVENTS, Producers.PAYMENT, PaymentRejectedV1.class),
            single("PaymentCancelled", Topics.PAYMENT_EVENTS, Producers.PAYMENT, PaymentCancelledV1.class),
            single("PaymentSettled", Topics.PAYMENT_EVENTS, Producers.PAYMENT, PaymentSettledV1.class),
            single("PaymentFailed", Topics.PAYMENT_EVENTS, Producers.PAYMENT, PaymentFailedV1.class),

            single("AssessPaymentRisk", Topics.FRAUD_COMMANDS, Producers.PAYMENT, AssessPaymentRiskV1.class),
            new EventDefinition("RiskAssessed", Topics.FRAUD_EVENTS, Producers.FRAUD,
                    Map.of(1, RiskAssessedV1.class, 2, RiskAssessedV2.class),
                    Map.of(1, v1 -> RiskAssessedV2.fromV1((RiskAssessedV1) v1))),

            single("ReserveFunds", Topics.FUNDS_COMMANDS, Producers.PAYMENT, ReserveFundsV1.class),
            single("CaptureFunds", Topics.FUNDS_COMMANDS, Producers.PAYMENT, CaptureFundsV1.class),
            single("ReleaseFunds", Topics.FUNDS_COMMANDS, Producers.PAYMENT, ReleaseFundsV1.class),
            single("FundsReserved", Topics.FUNDS_EVENTS, Producers.ACCOUNT, FundsReservedV1.class),
            single("FundsReservationFailed", Topics.FUNDS_EVENTS, Producers.ACCOUNT, FundsReservationFailedV1.class),
            single("FundsCaptured", Topics.FUNDS_EVENTS, Producers.ACCOUNT, FundsCapturedV1.class),
            single("FundsReleased", Topics.FUNDS_EVENTS, Producers.ACCOUNT, FundsReleasedV1.class),
            single("FundsDeposited", Topics.FUNDS_EVENTS, Producers.ACCOUNT, FundsDepositedV1.class),

            single("SubmitSettlement", Topics.SETTLEMENT_COMMANDS, Producers.PAYMENT, SubmitSettlementV1.class),
            single("SettlementCompleted", Topics.SETTLEMENT_EVENTS, Producers.SETTLEMENT, SettlementCompletedV1.class),
            single("SettlementDeclined", Topics.SETTLEMENT_EVENTS, Producers.SETTLEMENT, SettlementDeclinedV1.class)
    ).stream().collect(Collectors.toUnmodifiableMap(EventDefinition::eventType, Function.identity()));

    private EventCatalog() {
    }

    private static EventDefinition single(String type, String topic, String producer, Class<?> v1) {
        return new EventDefinition(type, topic, producer, Map.of(1, v1), Map.of());
    }

    public static Optional<EventDefinition> find(String eventType) {
        return Optional.ofNullable(DEFINITIONS.get(eventType));
    }

    public static EventDefinition get(String eventType) {
        return find(eventType).orElseThrow(() -> new IllegalArgumentException("Unknown event type " + eventType));
    }

    /** The definition whose current-version payload is {@code payloadType} (used by producers). */
    public static EventDefinition forPayload(Class<?> payloadType) {
        return DEFINITIONS.values().stream()
                .filter(d -> Objects.equals(d.payloadType(d.currentVersion()), payloadType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(payloadType + " is not a current contract version"));
    }

    public static Map<String, EventDefinition> all() {
        return new TreeMap<>(DEFINITIONS);
    }
}
