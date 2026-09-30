package com.payflow.settlement.application.usecase;

import com.payflow.settlement.application.port.in.ResumeParkedSettlementsUseCase;
import com.payflow.settlement.application.port.in.SubmitSettlementUseCase;
import com.payflow.settlement.application.port.out.SettlementEventPublisherPort;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayInstruction;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayResponse;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayUnavailableException;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort.DuplicateSettlementException;
import com.payflow.settlement.domain.Settlement;
import com.payflow.settlement.domain.SettlementRail;
import com.payflow.settlement.domain.SettlementStatus;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Submits a payment to its settlement rail using the "record intent, call out, record outcome" shape:
 *
 * <pre>
 *   TX1  find-or-create Settlement(PENDING)        -- unique(payment_id) makes this idempotent
 *   ---  gateway.submit(idempotencyKey=paymentId)  -- NO database transaction held open across the network call
 *   TX2  reload, complete/decline, optimistic-lock, outbox SettlementCompleted|Declined (same transaction)
 * </pre>
 *
 * A crash between TX1 and TX2 leaves a PENDING settlement. The command is redelivered (offset not committed)
 * or re-issued by saga recovery, and submit resumes with the same provider idempotency key, so the rail
 * deduplicates and money cannot move twice. A command for an already-terminal settlement re-announces the
 * recorded outcome, because the saga that asked again evidently did not receive it.
 *
 * <p>Circuit open (the rail is known to be down; nothing was sent): the settlement is <b>parked</b>, not failed. It
 * stays PENDING with the evidence recorded, and {@link #resumeParked} re-submits it once the circuit lets calls
 * through. Failing would cycle the command through the retry topics to the DLT and leave recovery to saga re-issues
 * (F-07: 1,373 dead letters, 446 s to recover).
 */
public class SubmitSettlementService implements SubmitSettlementUseCase, ResumeParkedSettlementsUseCase {

    /** A parked settlement is not re-tried sooner than this (natural backoff while the circuit stays open). */
    static final Duration PARKED_IDLE = Duration.ofSeconds(2);

    private final SettlementRepositoryPort settlements;
    private final SettlementGatewayRouter router;
    private final SettlementEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;
    private final boolean parking;

    public SubmitSettlementService(SettlementRepositoryPort settlements, SettlementGatewayRouter router,
                                   SettlementEventPublisherPort events, TransactionRunner tx, Clock clock) {
        this(settlements, router, events, tx, clock, true);
    }

    /**
     * @param parking false = kill switch back to the WP-03 behaviour: a circuit-open failure is thrown like any other
     *                transient rail failure (retry topics, DLT, saga re-issue)
     */
    public SubmitSettlementService(SettlementRepositoryPort settlements, SettlementGatewayRouter router,
                                   SettlementEventPublisherPort events, TransactionRunner tx, Clock clock,
                                   boolean parking) {
        this.settlements = settlements;
        this.router = router;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
        this.parking = parking;
    }

    @Override
    public SettlementResult submit(SubmitSettlementCommand command) {
        Settlement settlement = findOrCreate(command);
        if (parking && isParked(settlement)) {
            // Parked settlements have exactly one driver: the rate-limited resumer. A repeated command (saga recovery
            // re-issue, redelivery) must not re-submit it directly, or recovery after an outage bypasses the resume
            // budget and floods the pipeline (F-07: 868 re-issues drained parked work around the budget).
            return parkedResult(settlement);
        }
        return submit(settlement);
    }

    @Override
    public boolean isParked(UUID paymentId) {
        return parking && tx.readOnly(() -> settlements.findByPaymentId(paymentId)).map(SubmitSettlementService::isParked)
                .orElse(false);
    }

    private static boolean isParked(Settlement settlement) {
        return settlement.status() == SettlementStatus.PENDING
                && GatewayUnavailableException.CIRCUIT_OPEN.equals(settlement.lastErrorCode());
    }

    private static SettlementResult parkedResult(Settlement settlement) {
        return new SettlementResult(settlement.id(), settlement.paymentId(), false, null, null, true);
    }

    @Override
    public ResumeReport resumeParked(SettlementRail rail, int limit) {
        List<UUID> claimed = tx.inTransaction(() -> settlements.claimParked(rail, limit, PARKED_IDLE));
        int answered = 0;
        for (UUID paymentId : claimed) {
            Settlement parked = tx.readOnly(() -> settlements.findByPaymentId(paymentId)).orElseThrow();
            SettlementResult result = submit(parked);
            if (result.parked()) {
                // Still open: the rest would be refused the same way. Their claim expires; the next run retries.
                return new ResumeReport(claimed.size(), answered, true);
            }
            answered++;
        }
        return new ResumeReport(claimed.size(), answered, false);
    }

    private SettlementResult submit(Settlement settlement) {
        if (settlement.status().isTerminal()) {
            return tx.inTransaction(() -> {
                events.announceOutcome(settlement);
                return result(settlement);
            });
        }

        GatewayResponse response;
        try {
            response = router.gatewayFor(settlement.rail()).submit(new GatewayInstruction(
                    settlement.paymentId().toString(), settlement.amount(), settlement.paymentReference()));
        } catch (GatewayUnavailableException e) {
            // Still PENDING and resumable with the same key. Record the evidence (for a parked one, that is the
            // marker resumeParked looks for), then park or let the caller retry.
            boolean recorded = recordUnanswered(settlement.paymentId(), e);
            if (parking && e.circuitOpen() && recorded) {
                return parkedResult(settlement);
            }
            throw e;
        }

        return tx.inTransaction(() -> {
            Settlement current = settlements.findByPaymentId(settlement.paymentId()).orElseThrow();
            if (!current.status().isTerminal()) {
                if (response.accepted()) {
                    current.complete(response.providerReference(), clock.instant());
                } else {
                    current.decline(response.declineReason(), clock.instant());
                }
                settlements.update(current);
                events.announceOutcome(current);
            }
            return result(current);
        });
    }

    /** @return false if the evidence could not be written (then the settlement cannot be parked either) */
    private boolean recordUnanswered(UUID paymentId, GatewayUnavailableException failure) {
        try {
            tx.inTransaction(() -> {
                Settlement current = settlements.findByPaymentId(paymentId).orElseThrow();
                if (!current.status().isTerminal()) {
                    current.recordUnansweredAttempt(failure.outcome().name(), failure.code(), clock.instant());
                    settlements.update(current);
                }
                return null;
            });
            return true;
        } catch (RuntimeException evidenceNotRecorded) {
            // Best effort: the rail failure is the error that matters and is rethrown by the caller.
            failure.addSuppressed(evidenceNotRecorded);
            return false;
        }
    }

    private Settlement findOrCreate(SubmitSettlementCommand command) {
        try {
            return tx.inTransaction(() -> settlements.findByPaymentId(command.paymentId()).orElseGet(() -> {
                Settlement created = Settlement.initiate(command.paymentId(), railFor(command.method()),
                        command.amount(), command.paymentReference(), clock.instant());
                settlements.add(created);
                return created;
            }));
        } catch (DuplicateSettlementException raced) {
            return tx.readOnly(() -> settlements.findByPaymentId(command.paymentId())).orElseThrow();
        }
    }

    private static SettlementRail railFor(Method method) {
        return switch (method) {
            case CARD -> SettlementRail.CARD_NETWORK;
            case UPI -> SettlementRail.UPI;
            case BANK_TRANSFER -> SettlementRail.BANK_TRANSFER;
        };
    }

    private static SettlementResult result(Settlement s) {
        return new SettlementResult(s.id(), s.paymentId(), s.status() == SettlementStatus.COMPLETED,
                s.providerReference(), s.declineReason(), false);
    }
}
