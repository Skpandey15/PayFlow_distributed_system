package com.payflow.payment.adapter.in.web;

import com.payflow.payment.application.port.in.CancelPaymentUseCase;
import com.payflow.payment.application.port.in.CreatePaymentUseCase;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentResult;
import com.payflow.payment.application.port.in.GetPaymentSagaUseCase;
import com.payflow.payment.application.port.in.GetPaymentSagaUseCase.SagaView;
import com.payflow.payment.application.port.in.GetPaymentUseCase;
import com.payflow.payment.application.port.in.ListPaymentsUseCase;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.domain.saga.CheckoutContext;
import com.payflow.platform.observability.CorrelationIdFilter;
import com.payflow.platform.web.PageResponse;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Inbound HTTP adapter. It translates HTTP to use-case commands and results back to HTTP, and does
 * nothing else: no business rules, no repositories, no transactions (enforced by ArchUnit).
 */
@RestController
@RequestMapping(path = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Payments")
class PaymentController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final CreatePaymentUseCase createPayment;
    private final GetPaymentUseCase getPayment;
    private final ListPaymentsUseCase listPayments;
    private final CancelPaymentUseCase cancelPayment;
    private final GetPaymentSagaUseCase getPaymentSaga;

    PaymentController(CreatePaymentUseCase createPayment, GetPaymentUseCase getPayment,
                      ListPaymentsUseCase listPayments, CancelPaymentUseCase cancelPayment,
                      GetPaymentSagaUseCase getPaymentSaga) {
        this.createPayment = createPayment;
        this.getPayment = getPayment;
        this.listPayments = listPayments;
        this.cancelPayment = cancelPayment;
        this.getPaymentSaga = getPaymentSaga;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a payment (idempotent per Idempotency-Key); processing continues asynchronously",
            description = "Returns 201 with status CREATED once the payment and its workflow are durably recorded. "
                    + "Risk assessment, funds reservation, settlement and capture then run as a saga; poll GET "
                    + "for the final status. Replays of the same key and payload return 201 with the original "
                    + "payment and `Idempotent-Replayed: true`. Reusing a key with a different payload returns 422.")
    ResponseEntity<PaymentResponse> create(
            Actor actor,
            @Parameter(description = "Client-generated unique key, e.g. a UUID. Retention: at least 24h.")
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentRequest.Checkout c = request.checkout();
        CheckoutContext checkout = c == null ? CheckoutContext.NONE
                : new CheckoutContext(c.deviceId(), c.ipAddress(), c.userAgent(), c.countryCode());
        CreatePaymentResult result = createPayment.create(new CreatePaymentCommand(actor, idempotencyKey,
                new AccountId(request.payerAccountId()), new AccountId(request.payeeAccountId()),
                Money.of(request.amount(), request.currency()), request.method(), request.reference(), checkout,
                CorrelationIdFilter.current()));
        PaymentResponse body = PaymentResponse.from(result.payment());
        return ResponseEntity.created(URI.create("/api/v1/payments/" + body.id()))
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()))
                .body(body);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment visible to the caller")
    PaymentResponse get(Actor actor, @PathVariable UUID paymentId) {
        return PaymentResponse.from(getPayment.get(actor, new PaymentId(paymentId)));
    }

    @GetMapping
    @Operation(summary = "List the caller's payments, newest first")
    PageResponse<PaymentResponse> list(Actor actor,
                                       @RequestParam(required = false) PaymentStatus status,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        return PageResponse.from(listPayments.list(actor, status, new PageQuery(page, size)), PaymentResponse::from);
    }

    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "Cancel a payment before processing (idempotent)")
    PaymentResponse cancel(Actor actor, @PathVariable UUID paymentId) {
        return PaymentResponse.from(cancelPayment.cancel(actor, new PaymentId(paymentId)));
    }

    @GetMapping("/{paymentId}/saga")
    @Operation(summary = "Workflow state of a payment, for operators debugging stuck payments (requires payments:admin)")
    SagaView saga(Actor actor, @PathVariable UUID paymentId) {
        return getPaymentSaga.get(actor, new PaymentId(paymentId));
    }
}
