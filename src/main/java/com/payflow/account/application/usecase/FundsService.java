package com.payflow.account.application.usecase;

import com.payflow.account.application.port.in.AccountView;
import com.payflow.account.application.port.in.DepositFundsUseCase;
import com.payflow.account.application.port.in.FundsCommandUseCase;
import com.payflow.account.application.port.out.AccountBalanceRepositoryPort;
import com.payflow.account.application.port.out.AccountRepositoryPort;
import com.payflow.account.application.port.out.DepositRepositoryPort;
import com.payflow.account.application.port.out.DepositRepositoryPort.DepositRecord;
import com.payflow.account.application.port.out.FundsEventPublisherPort;
import com.payflow.account.application.port.out.FundsReservationRepositoryPort;
import com.payflow.account.domain.Account;
import com.payflow.account.domain.AccountBalance;
import com.payflow.account.domain.FundsReservation;
import com.payflow.account.domain.ReservationStatus;
import com.payflow.shared.application.ConflictException;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.TransactionRunner;
import com.payflow.shared.application.UnprocessableException;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Funds reservation, capture and release (the Account context's saga participant) and treasury deposits.
 *
 * <p>Every mutation is one local transaction that:
 * <ol>
 *   <li>locks the affected balance rows ({@code FOR UPDATE}, always in account-id order to avoid deadlocks)</li>
 *   <li>re-reads the reservation <em>after</em> the lock. A concurrent duplicate command for the same payment
 *       waits on the lock, then finds the committed reservation and re-announces it instead of reserving twice.</li>
 *   <li>applies the domain change</li>
 *   <li>writes the outcome event to the outbox</li>
 * </ol>
 */
public class FundsService implements FundsCommandUseCase, DepositFundsUseCase {

    public static final String PERMISSION_DEPOSIT = "funds:deposit";

    private final AccountRepositoryPort accounts;
    private final AccountBalanceRepositoryPort balances;
    private final FundsReservationRepositoryPort reservations;
    private final DepositRepositoryPort deposits;
    private final FundsEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;

    public FundsService(AccountRepositoryPort accounts, AccountBalanceRepositoryPort balances,
                        FundsReservationRepositoryPort reservations, DepositRepositoryPort deposits,
                        FundsEventPublisherPort events, TransactionRunner tx, Clock clock) {
        this.accounts = accounts;
        this.balances = balances;
        this.reservations = reservations;
        this.deposits = deposits;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void reserve(ReserveFunds command) {
        tx.inTransaction(() -> {
            Instant now = clock.instant();
            Optional<AccountBalance> payerBalance = balances.findForUpdate(command.payerAccountId());
            Optional<FundsReservation> existing = reservations.findByPaymentId(command.paymentId());
            if (existing.isPresent()) {
                reannounceReservation(existing.get());
                return null;
            }
            String rejection = rejectionReason(command, payerBalance);
            if (rejection != null) {
                reservations.add(FundsReservation.rejected(command.paymentId(), command.payerAccountId(),
                        command.payeeAccountId(), command.amount(), rejection, now));
                events.reservationFailed(command.paymentId(), rejection);
                return null;
            }
            AccountBalance balance = payerBalance.orElseThrow();
            balance.reserve(command.amount(), now);
            balances.update(balance);
            FundsReservation reservation = FundsReservation.reserved(command.paymentId(), command.payerAccountId(),
                    command.payeeAccountId(), command.amount(), now);
            reservations.add(reservation);
            events.fundsReserved(reservation);
            return null;
        });
    }

    @Override
    public void capture(UUID paymentId) {
        tx.inTransaction(() -> {
            Instant now = clock.instant();
            FundsReservation reservation = reservations.findByPaymentId(paymentId)
                    .orElseThrow(() -> new NotFoundException("FUNDS_RESERVATION_NOT_FOUND",
                            "No reservation for payment " + paymentId));
            if (reservation.status() == ReservationStatus.RELEASED || reservation.status() == ReservationStatus.REJECTED) {
                reservation.capture(now); // throws: capturing released/rejected funds is a protocol violation
            }
            AccountId payer = reservation.payerAccountId();
            AccountId payee = reservation.payeeAccountId();
            // Deterministic lock order (by account id): two opposite-direction captures cannot deadlock.
            Map<AccountId, AccountBalance> locked = new HashMap<>();
            Stream.of(payer, payee).sorted(Comparator.comparing(AccountId::value))
                    .forEach(id -> locked.put(id, balances.findForUpdate(id).orElseThrow()));
            FundsReservation current = reservations.findByPaymentId(paymentId).orElseThrow();
            if (current.status() == ReservationStatus.CAPTURED) {
                events.fundsCaptured(current); // idempotent re-announcement
                return null;
            }
            current.capture(now); // RELEASED/REJECTED -> InvalidStateTransition (permanent, alerts via DLT)
            AccountBalance payerBalance = locked.get(payer);
            AccountBalance payeeBalance = locked.get(payee);
            payerBalance.captureReserved(current.amount(), now);
            payeeBalance.credit(current.amount(), now);
            balances.update(payerBalance);
            balances.update(payeeBalance);
            reservations.update(current);
            events.fundsCaptured(current);
            return null;
        });
    }

    @Override
    public void release(ReleaseFunds command) {
        tx.inTransaction(() -> {
            Instant now = clock.instant();
            Optional<AccountBalance> payerBalance = balances.findForUpdate(command.payerAccountId());
            Optional<FundsReservation> existing = reservations.findByPaymentId(command.paymentId());
            if (existing.isEmpty()) {
                // The release overtook its reservation: leave a tombstone so the late reserve is refused.
                reservations.add(FundsReservation.releasedWithoutReservation(command.paymentId(),
                        command.payerAccountId(), command.amount(), command.reason(), now));
                events.fundsReleased(command.paymentId(), command.payerAccountId(), command.amount(), command.reason());
                return null;
            }
            FundsReservation reservation = existing.get();
            switch (reservation.status()) {
                case RESERVED -> {
                    reservation.release(command.reason(), now);
                    AccountBalance balance = payerBalance.orElseThrow();
                    balance.releaseReserved(reservation.amount(), now);
                    balances.update(balance);
                    reservations.update(reservation);
                }
                case RELEASED, REJECTED -> { /* nothing held: duplicate compensation is a no-op */ }
                case CAPTURED -> throw new InvalidStateTransitionException("FUNDS_ALREADY_CAPTURED",
                        "Funds for payment " + command.paymentId() + " were captured; a refund is required");
            }
            events.fundsReleased(command.paymentId(), reservation.payerAccountId(), reservation.amount(), command.reason());
            return null;
        });
    }

    @Override
    public DepositResult deposit(DepositCommand command) {
        requirePermission(command.actor(), PERMISSION_DEPOSIT);
        return tx.inTransaction(() -> {
            Optional<DepositRecord> previous = deposits.find(command.depositId());
            if (previous.isPresent()) {
                DepositRecord p = previous.get();
                if (!p.accountId().equals(command.accountId()) || !p.amount().equals(command.amount())) {
                    throw new ConflictException("DEPOSIT_ID_REUSED", "depositId was already used for a different deposit");
                }
                return new DepositResult(p.depositId(), view(command.accountId()), true);
            }
            Account account = accounts.findById(command.accountId())
                    .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + command.accountId() + " not found"));
            if (!command.amount().hasCurrency(account.currency())) {
                throw new UnprocessableException("CURRENCY_MISMATCH", "Deposit currency must match the account currency");
            }
            Instant now = clock.instant();
            AccountBalance balance = balances.findForUpdate(command.accountId()).orElseThrow();
            balance.credit(command.amount(), now);
            balances.update(balance);
            DepositRecord deposit = new DepositRecord(command.depositId(), command.accountId(), command.amount(),
                    command.actor().subject(), now);
            deposits.add(deposit);
            events.fundsDeposited(deposit);
            return new DepositResult(deposit.depositId(), AccountView.from(account, balance), false);
        });
    }

    private AccountView view(AccountId accountId) {
        Account account = accounts.findById(accountId).orElseThrow();
        return AccountView.from(account, balances.find(accountId).orElseThrow());
    }

    private void reannounceReservation(FundsReservation r) {
        switch (r.status()) {
            case RESERVED, CAPTURED -> events.fundsReserved(r);
            case REJECTED -> events.reservationFailed(r.paymentId(), r.reason());
            case RELEASED -> events.reservationFailed(r.paymentId(), "RESERVATION_ALREADY_RELEASED");
        }
    }

    private String rejectionReason(ReserveFunds command, Optional<AccountBalance> payerBalance) {
        Optional<Account> payer = accounts.findById(command.payerAccountId());
        Optional<Account> payee = accounts.findById(command.payeeAccountId());
        if (payer.isEmpty() || payerBalance.isEmpty() || !payer.get().canTransact()) {
            return "PAYER_ACCOUNT_INELIGIBLE";
        }
        if (payee.isEmpty() || !payee.get().canTransact()) {
            return "PAYEE_ACCOUNT_INELIGIBLE";
        }
        Money amount = command.amount();
        if (!amount.hasCurrency(payer.get().currency()) || !amount.hasCurrency(payee.get().currency())) {
            return "CURRENCY_MISMATCH";
        }
        if (!payerBalance.get().canReserve(amount)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }
}
