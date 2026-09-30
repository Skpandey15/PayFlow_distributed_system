package com.payflow.settlement.application.port.out;

import com.payflow.settlement.domain.SettlementRail;
import com.payflow.shared.application.DependencyUnavailableException;
import com.payflow.shared.application.UnprocessableException;
import com.payflow.shared.domain.Money;

import java.util.Optional;

/**
 * Strategy + Adapter boundary to one external settlement rail (card network, UPI switch, bank).
 *
 * <p>WP-03 resilience (timeouts, bounded retry with backoff and jitter, circuit breaker, bulkhead) lives in the
 * implementation of this port, never in the domain or the use cases. Implementations must honour
 * {@code idempotencyKey} so a retried instruction cannot settle twice.
 *
 * <p>Failure semantics are explicit, because for money "we do not know" is different from "it failed":
 * <ul>
 *   <li>a {@link GatewayResponse} is a <b>known</b> outcome (accepted or declined by the rail)</li>
 *   <li>{@link GatewayUnavailableException} with {@link DeliveryOutcome#NOT_SENT}: the instruction provably did not
 *       reach the rail (connection refused, circuit open, bulkhead full, provider throttling). Nothing happened.</li>
 *   <li>{@link GatewayUnavailableException} with {@link DeliveryOutcome#UNKNOWN}: it may have been processed (timeout,
 *       5xx, connection lost after sending). Never treat this as a decline: retry with the same key, or inquire.</li>
 *   <li>{@link InstructionRejectedException}: the rail refused the request itself (malformed, 4xx). Permanent.</li>
 * </ul>
 */
public interface SettlementGatewayPort {

    SettlementRail rail();

    /** Submits (or idempotently re-submits) an instruction. */
    GatewayResponse submit(GatewayInstruction instruction);

    /**
     * Asks the rail what it recorded for {@code idempotencyKey}. Empty = the rail never received it. Used by manual
     * review to resolve an UNKNOWN outcome with evidence instead of guessing.
     */
    Optional<GatewayResponse> inquire(String idempotencyKey);

    /**
     * Blocks {@code idempotencyKey} at the rail so an instruction still in transit can never settle later.
     * Idempotent. Returns the rail's resulting record: a decline ({@code VOIDED}, or the earlier decline), or the
     * earlier acceptance, in which case the money has moved and the void did nothing.
     */
    GatewayResponse voidInstruction(String idempotencyKey);

    record GatewayInstruction(String idempotencyKey, Money amount, String paymentReference) {
    }

    record GatewayResponse(boolean accepted, String providerReference, String declineReason) {

        public static GatewayResponse accepted(String providerReference) {
            return new GatewayResponse(true, providerReference, null);
        }

        public static GatewayResponse declined(String reason) {
            return new GatewayResponse(false, null, reason);
        }
    }

    enum DeliveryOutcome {
        /** Provably not delivered to the rail: nothing can have happened there. */
        NOT_SENT,
        /** Possibly processed by the rail: the outcome must be recovered (same-key retry or inquiry). */
        UNKNOWN
    }

    /** Transient: retryable with the same idempotency key. */
    class GatewayUnavailableException extends DependencyUnavailableException {

        /**
         * The rail's circuit is open: refused locally, NOT_SENT. The rail is known to be unhealthy, so retrying soon
         * is pointless. The caller should park the work until the circuit lets calls through again.
         */
        public static final String CIRCUIT_OPEN = "SETTLEMENT_RAIL_CIRCUIT_OPEN";

        private final DeliveryOutcome outcome;

        public GatewayUnavailableException(SettlementRail rail, String code, DeliveryOutcome outcome, String message,
                                           Throwable cause) {
            super(code, "Settlement rail " + rail + " unavailable (" + outcome + "): " + message, cause);
            this.outcome = outcome;
        }

        public GatewayUnavailableException(SettlementRail rail, String message, Throwable cause) {
            this(rail, "SETTLEMENT_RAIL_UNAVAILABLE", DeliveryOutcome.UNKNOWN, message, cause);
        }

        public DeliveryOutcome outcome() {
            return outcome;
        }

        public boolean circuitOpen() {
            return CIRCUIT_OPEN.equals(code());
        }
    }

    /** Permanent: the rail rejected the request as invalid. Retrying the same request cannot succeed. */
    class InstructionRejectedException extends UnprocessableException {
        public InstructionRejectedException(SettlementRail rail, int status) {
            super("SETTLEMENT_RAIL_REJECTED_INSTRUCTION", "Settlement rail " + rail + " rejected the instruction (HTTP "
                    + status + ")");
        }
    }
}
