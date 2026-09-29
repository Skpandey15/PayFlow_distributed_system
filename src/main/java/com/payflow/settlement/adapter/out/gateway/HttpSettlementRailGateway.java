package com.payflow.settlement.adapter.out.gateway;

import com.payflow.platform.messaging.MessageContext;
import com.payflow.settlement.application.port.out.SettlementGatewayPort;
import com.payflow.settlement.domain.SettlementRail;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.payflow.settlement.application.port.out.SettlementGatewayPort.DeliveryOutcome.NOT_SENT;
import static com.payflow.settlement.application.port.out.SettlementGatewayPort.DeliveryOutcome.UNKNOWN;

/**
 * HTTP adapter to one settlement rail, with the resilience policy of that dependency applied as explicit
 * decorators (no AOP proxies), innermost first:
 *
 * <pre>
 *   Retry( CircuitBreaker( Bulkhead( HTTP call with connect + response timeout ) ) )
 * </pre>
 * <ul>
 *   <li><b>Timeout</b> (HTTP client): bounded connect and response wait. A response timeout means UNKNOWN outcome.</li>
 *   <li><b>Bulkhead</b> (semaphore, fail fast): caps concurrent calls to the rail at the provider's contracted
 *       concurrency. A separate, smaller bulkhead serves operator inquiries, so neither workload can take the
 *       other's share of the provider.</li>
 *   <li><b>Circuit breaker</b> (per rail): stops calling a rail that is failing or slow; calls fail fast as NOT_SENT
 *       while it is open. Bulkhead rejections and invalid-instruction answers are not held against the rail.</li>
 *   <li><b>Retry</b>: only failures where an immediate retry has a real chance and cannot amplify an overload
 *       (connection refused, 503), at most once more, with exponential backoff and jitter. Timeouts, throttling (429),
 *       an open circuit and a full bulkhead are NOT retried here: the Kafka retry topics retry later with a much
 *       longer backoff, and saga recovery after that. Retrying is safe because every call carries the payment's
 *       idempotency key.</li>
 * </ul>
 * This adapter does not log failures (the Kafka or HTTP boundary logs them once). It enriches the logging context
 * with the dependency, the circuit state and the delivery outcome, so that single log line explains the failure.
 */
public class HttpSettlementRailGateway implements SettlementGatewayPort {

    static final String UNREACHABLE = "SETTLEMENT_RAIL_UNREACHABLE";
    static final String TIMEOUT = "SETTLEMENT_RAIL_TIMEOUT";
    static final String CONNECTION_LOST = "SETTLEMENT_RAIL_CONNECTION_LOST";
    static final String THROTTLED = "SETTLEMENT_RAIL_THROTTLED";
    static final String UNAVAILABLE = "SETTLEMENT_RAIL_UNAVAILABLE";
    static final String SERVER_ERROR = "SETTLEMENT_RAIL_ERROR";
    static final String CIRCUIT_OPEN = GatewayUnavailableException.CIRCUIT_OPEN;
    static final String BULKHEAD_FULL = "SETTLEMENT_RAIL_BULKHEAD_FULL";

    private final SettlementRail rail;
    private final RestClient submitClient;
    private final RestClient inquiryClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead paymentBulkhead;
    private final Bulkhead inquiryBulkhead;
    private final RailCallMetrics metrics;

    public HttpSettlementRailGateway(SettlementRail rail, RestClient submitClient, RestClient inquiryClient,
                                     CircuitBreaker circuitBreaker, Retry retry, Bulkhead paymentBulkhead,
                                     Bulkhead inquiryBulkhead, RailCallMetrics metrics) {
        this.rail = rail;
        this.submitClient = submitClient;
        this.inquiryClient = inquiryClient;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.paymentBulkhead = paymentBulkhead;
        this.inquiryBulkhead = inquiryBulkhead;
        this.metrics = metrics;
    }

    @Override
    public SettlementRail rail() {
        return rail;
    }

    record RailAnswer(String status, String providerReference, String declineReason) {
        GatewayResponse toResponse() {
            return "ACCEPTED".equals(status) ? GatewayResponse.accepted(providerReference)
                    : GatewayResponse.declined(declineReason);
        }
    }

    @Override
    public GatewayResponse submit(GatewayInstruction instruction) {
        Map<String, String> body = Map.of("idempotencyKey", instruction.idempotencyKey(),
                "amount", instruction.amount().amount().toPlainString(),
                "currency", instruction.amount().currencyCode(),
                "reference", instruction.paymentReference() == null ? "" : instruction.paymentReference());
        GatewayResponse response = resilient("submit", paymentBulkhead, true, () -> submitClient.post()
                .uri("/rails/{rail}/transfers", rail.name())
                .body(body)
                .retrieve()
                .body(RailAnswer.class)
                .toResponse());
        metrics.outcome(rail, "submit", response.accepted() ? "ACCEPTED" : "DECLINED");
        return response;
    }

    @Override
    public Optional<GatewayResponse> inquire(String idempotencyKey) {
        Optional<GatewayResponse> answer = resilient("inquire", inquiryBulkhead, false, () -> inquiryClient.get()
                .uri("/rails/{rail}/transfers/{key}", rail.name(), idempotencyKey)
                .exchange((request, response) -> response.getStatusCode().value() == 404
                        ? Optional.<GatewayResponse>empty()
                        : Optional.of(readOrThrow(response.getStatusCode(), () -> response.bodyTo(RailAnswer.class)))));
        metrics.outcome(rail, "inquire", answer.map(r -> r.accepted() ? "ACCEPTED" : "DECLINED").orElse("NOT_FOUND"));
        return answer;
    }

    @Override
    public GatewayResponse voidInstruction(String idempotencyKey) {
        GatewayResponse answer = resilient("void", inquiryBulkhead, false, () -> inquiryClient.post()
                .uri("/rails/{rail}/transfers/{key}/void", rail.name(), idempotencyKey)
                // 409 = the rail already accepted this key: a valid answer (the money moved), not an error.
                .exchange((request, response) -> response.getStatusCode().value() == 409
                        ? response.bodyTo(RailAnswer.class).toResponse()
                        : readOrThrow(response.getStatusCode(), () -> response.bodyTo(RailAnswer.class))));
        metrics.outcome(rail, "void", answer.accepted() ? "ALREADY_ACCEPTED" : "VOIDED");
        return answer;
    }

    private GatewayResponse readOrThrow(HttpStatusCode status, Supplier<RailAnswer> body) {
        if (!status.is2xxSuccessful()) {
            throw translateStatus(status.value(), null);
        }
        return body.get().toResponse();
    }

    /**
     * Applies the decorators and translates every failure into the port's vocabulary. Operator calls (inquiry,
     * void) go through the same circuit breaker but are never retried: a human is waiting and decides.
     */
    private <T> T resilient(String operation, Bulkhead bulkhead, boolean retryable, Supplier<T> call) {
        Supplier<T> guarded = Bulkhead.decorateSupplier(bulkhead, () -> translate(call));
        guarded = CircuitBreaker.decorateSupplier(circuitBreaker, guarded);
        if (retryable) {
            guarded = Retry.decorateSupplier(retry, guarded);
        }
        try {
            return guarded.get();
        } catch (CallNotPermittedException e) {
            throw failure(operation, CIRCUIT_OPEN, NOT_SENT, "circuit breaker open", null);
        } catch (BulkheadFullException e) {
            throw failure(operation, BULKHEAD_FULL, NOT_SENT, "concurrency limit reached", null);
        } catch (GatewayUnavailableException e) {
            throw failure(operation, e.code(), e.outcome(), e.getMessage(), e);
        } catch (InstructionRejectedException e) {
            metrics.outcome(rail, operation, e.code());
            throw e;
        }
    }

    private <T> T translate(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            throw translateStatus(e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            // Walk the whole chain: the JDK client wraps the socket-level cause at different depths per failure.
            // Connect-phase failures are checked first: only they prove the instruction never left (NOT_SENT).
            if (causedBy(e, HttpConnectTimeoutException.class) || causedBy(e, ConnectException.class)) {
                throw new GatewayUnavailableException(rail, UNREACHABLE, NOT_SENT, "connection failed", e);
            }
            if (causedBy(e, HttpTimeoutException.class)) {
                throw new GatewayUnavailableException(rail, TIMEOUT, UNKNOWN, "no response within the timeout", e);
            }
            throw new GatewayUnavailableException(rail, CONNECTION_LOST, UNKNOWN, "connection lost", e);
        }
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private RuntimeException translateStatus(int status, Throwable cause) {
        if (status == 429) {
            return new GatewayUnavailableException(rail, THROTTLED, NOT_SENT, "provider throttling (429)", cause);
        }
        if (status == 503) {
            return new GatewayUnavailableException(rail, UNAVAILABLE, UNKNOWN, "provider unavailable (503)", cause);
        }
        if (status >= 500) {
            return new GatewayUnavailableException(rail, SERVER_ERROR, UNKNOWN, "provider error " + status, cause);
        }
        return new InstructionRejectedException(rail, status);
    }

    private GatewayUnavailableException failure(String operation, String code, DeliveryOutcome outcome, String message,
                                                Throwable cause) {
        metrics.outcome(rail, operation, code);
        MessageContext.put(MessageContext.DEPENDENCY, "settlement-rail:" + rail);
        MessageContext.put(MessageContext.CIRCUIT_BREAKER_STATE, circuitBreaker.getState());
        MessageContext.put(MessageContext.DELIVERY_OUTCOME, outcome);
        return cause instanceof GatewayUnavailableException g && g.code().equals(code) ? g
                : new GatewayUnavailableException(rail, code, outcome, message, cause);
    }

    CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }
}
