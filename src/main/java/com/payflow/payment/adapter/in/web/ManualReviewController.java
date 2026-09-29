package com.payflow.payment.adapter.in.web;

import com.payflow.payment.application.port.in.ManualReviewUseCase;
import com.payflow.payment.application.port.in.ManualReviewUseCase.CaseDetail;
import com.payflow.payment.application.port.in.ManualReviewUseCase.DecideCommand;
import com.payflow.payment.application.port.in.ManualReviewUseCase.Decision;
import com.payflow.payment.application.port.in.ManualReviewUseCase.DecisionResult;
import com.payflow.payment.application.port.in.ManualReviewUseCase.ReviewCase;
import com.payflow.payment.domain.PaymentId;
import com.payflow.platform.web.PageResponse;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operations API for payments whose outcome is unknown (saga MANUAL_REVIEW). Requires {@code ops:manual-review}
 * (route rule + use-case check), is rate limited per operator, and every decision needs an Idempotency-Key and a
 * reason. This boundary writes the operator audit log line; the durable audit record is written by the use case.
 */
@RestController
@Validated
@Tag(name = "Operations: manual review")
@RequestMapping(path = "/api/v1/ops/manual-reviews", produces = MediaType.APPLICATION_JSON_VALUE)
class ManualReviewController {

    private static final Logger log = LoggerFactory.getLogger("payflow.ops.audit");

    private final ManualReviewUseCase reviews;
    private final MeterRegistry meters;

    ManualReviewController(ManualReviewUseCase reviews, MeterRegistry meters) {
        this.reviews = reviews;
        this.meters = meters;
    }

    record DecisionRequest(@NotNull Decision decision, @NotBlank @Size(max = 500) String reason,
                           @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._:/-]*$") String ticketReference) {
    }

    @GetMapping
    @Operation(summary = "Payments awaiting manual review, oldest first")
    PageResponse<ReviewCase> list(Actor actor, @RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        return PageResponse.from(reviews.list(actor, new PageQuery(page, size)), c -> c);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Case detail: payment, our settlement record, a live rail inquiry, allowed decisions, history")
    CaseDetail get(Actor actor, @PathVariable UUID paymentId) {
        return reviews.get(actor, new PaymentId(paymentId));
    }

    @PostMapping(path = "/{paymentId}/decisions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Resolve a case: RESUME or CONFIRM_NOT_SETTLED (idempotent per Idempotency-Key)")
    DecisionResult decide(Actor actor, @PathVariable UUID paymentId,
                          @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100)
                          @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String idempotencyKey,
                          @Valid @RequestBody DecisionRequest request) {
        DecisionResult result = reviews.decide(actor, new DecideCommand(new PaymentId(paymentId), request.decision(),
                request.reason(), request.ticketReference(), idempotencyKey));
        if (!result.replayed()) {
            Counter.builder("payflow.manual_review.decisions").tag("decision", result.decision().name())
                    .register(meters).increment();
            log.atInfo().addKeyValue("operator", actor.subject()).addKeyValue("aggregateId", paymentId)
                    .addKeyValue("decision", result.decision()).addKeyValue("resumedStep", result.resumedStep())
                    .addKeyValue("decisionId", result.decisionId())
                    .log("manual review decision recorded");
        }
        return result;
    }
}
