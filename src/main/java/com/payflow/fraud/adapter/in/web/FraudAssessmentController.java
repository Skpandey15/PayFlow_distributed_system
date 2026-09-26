package com.payflow.fraud.adapter.in.web;

import com.payflow.fraud.application.port.in.GetFraudAssessmentUseCase;
import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/fraud", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Fraud")
class FraudAssessmentController {

    private final GetFraudAssessmentUseCase assessments;

    FraudAssessmentController(GetFraudAssessmentUseCase assessments) {
        this.assessments = assessments;
    }

    record SignalResponse(String code, int score, String detail) {
    }

    record ChannelResponse(String deviceId, String ipAddress, String userAgent, String countryCode) {
    }

    record FraudAssessmentResponse(UUID id, UUID paymentId, int riskScore, String decision,
                                   List<SignalResponse> signals, ChannelResponse channel, String modelVersion,
                                   Instant assessedAt) {
    }

    @GetMapping("/assessments/{paymentId}")
    @Operation(summary = "Risk assessment evidence for a payment (analysts; requires fraud:read)")
    FraudAssessmentResponse get(Actor actor, @PathVariable UUID paymentId) {
        FraudAssessment a = assessments.getByPaymentId(actor, paymentId);
        return new FraudAssessmentResponse(a.id(), a.paymentId(), a.riskScore(), a.decision().name(),
                a.signals().stream().map(s -> new SignalResponse(s.code(), s.score(), s.detail())).toList(),
                new ChannelResponse(a.channel().deviceId(), a.channel().ipAddress(), a.channel().userAgent(),
                        a.channel().countryCode()),
                a.modelVersion(), a.assessedAt());
    }
}
