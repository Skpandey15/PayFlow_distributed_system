package com.payflow.settlement.application.port.in;

import com.payflow.shared.domain.Money;

import java.util.UUID;

/**
 * Published command API of the Settlement context. Idempotent per payment: resubmitting a payment that
 * already has a terminal settlement returns the recorded outcome, and resubmitting a PENDING one resumes
 * the provider call with the same idempotency key.
 *
 * <p>Throws {@link com.payflow.shared.application.DependencyUnavailableException} when the rail cannot
 * be reached. The settlement then stays PENDING and the call can be retried safely.
 */
public interface SubmitSettlementUseCase {

    SettlementResult submit(SubmitSettlementCommand command);

    enum Method { CARD, UPI, BANK_TRANSFER }

    record SubmitSettlementCommand(UUID paymentId, Method method, Money amount, String paymentReference) {
    }

    record SettlementResult(UUID settlementId, UUID paymentId, boolean completed, String providerReference,
                            String declineReason) {
    }
}
