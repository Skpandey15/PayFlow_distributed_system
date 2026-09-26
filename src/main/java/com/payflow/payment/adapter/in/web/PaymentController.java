package com.payflow.payment.adapter.in.web;

import com.payflow.payment.application.port.in.AuthorizePaymentUseCase;
import com.payflow.payment.application.port.in.AuthorizePaymentUseCase.AuthorizePaymentCommand;
import com.payflow.payment.application.port.in.AuthorizePaymentUseCase.CheckoutChannel;
import com.payflow.payment.application.port.in.CancelPaymentUseCase;
import com.payflow.payment.application.port.in.CreatePaymentUseCase;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentResult;
import com.payflow.payment.application.port.in.GetPaymentUseCase;
import com.payflow.payment.application.port.in.ListPaymentsUseCase;
import com.payflow.payment.application.port.in.ProcessPaymentUseCase;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
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
    private final AuthorizePaymentUseCase authorizePayment;
    private final ProcessPaymentUseCase processPayment;

    PaymentController(CreatePaymentUseCase createPayment, GetPaymentUseCase getPayment,
                      ListPaymentsUseCase listPayments, CancelPaymentUseCase cancelPayment,
                      AuthorizePaymentUseCase authorizePayment, ProcessPaymentUseCase processPayment) {
        this.createPayment = createPayment;
        this.getPayment = getPayment;
        this.listPayments = listPayments;
        this.cancelPayment = cancelPayment;
        this.authorizePayment = authorizePayment;
        this.processPayment = processPayment;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a payment (idempotent per Idempotency-Key)",
            description = "Replays of the same key and payload return 201 with the original payment and "
                    + "`Idempotent-Replayed: true`. Reusing a key with a different payload returns 422.")
    ResponseEntity<PaymentResponse> create(
            Actor actor,
            @Parameter(description = "Client-generated unique key, e.g. a UUID. Retention: at least 24h.")
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentResult result = createPayment.create(new CreatePaymentCommand(actor, idempotencyKey,
                new AccountId(request.payerAccountId()), new AccountId(request.payeeAccountId()),
                Money.of(request.amount(), request.currency()), request.method(), request.reference()));
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

    @PostMapping("/{paymentId}/authorize")
    @Operation(summary = "Run eligibility and risk checks (requires payments:process)")
    PaymentResponse authorize(Actor actor, @PathVariable UUID paymentId,
                              @Valid @RequestBody(required = false) AuthorizePaymentRequest request) {
        CheckoutChannel channel = request == null ? null
                : new CheckoutChannel(request.deviceId(), request.ipAddress(), request.userAgent(), request.countryCode());
        return PaymentResponse.from(authorizePayment.authorize(
                new AuthorizePaymentCommand(actor, new PaymentId(paymentId), channel)));
    }

    @PostMapping("/{paymentId}/process")
    @Operation(summary = "Submit an authorized payment for settlement (resumable, requires payments:process)")
    PaymentResponse process(Actor actor, @PathVariable UUID paymentId) {
        return PaymentResponse.from(processPayment.process(actor, new PaymentId(paymentId)));
    }
}
