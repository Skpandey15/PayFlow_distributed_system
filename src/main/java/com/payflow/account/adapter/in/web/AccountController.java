package com.payflow.account.adapter.in.web;

import com.payflow.account.application.port.in.AccountView;
import com.payflow.account.application.port.in.DepositFundsUseCase;
import com.payflow.account.application.port.in.DepositFundsUseCase.DepositCommand;
import com.payflow.account.application.port.in.DepositFundsUseCase.DepositResult;
import com.payflow.account.application.port.in.FreezeAccountUseCase;
import com.payflow.account.application.port.in.GetAccountUseCase;
import com.payflow.account.application.port.in.OpenAccountUseCase;
import com.payflow.account.application.port.in.OpenAccountUseCase.OpenAccountCommand;
import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts")
class AccountController {

    private final OpenAccountUseCase openAccount;
    private final GetAccountUseCase getAccount;
    private final FreezeAccountUseCase freezeAccount;
    private final DepositFundsUseCase depositFunds;

    AccountController(OpenAccountUseCase openAccount, GetAccountUseCase getAccount, FreezeAccountUseCase freezeAccount,
                      DepositFundsUseCase depositFunds) {
        this.openAccount = openAccount;
        this.getAccount = getAccount;
        this.freezeAccount = freezeAccount;
        this.depositFunds = depositFunds;
    }

    record OpenAccountRequest(@NotBlank @Size(max = 100) String displayName,
                              @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {
    }

    record AccountResponse(UUID id, String ownerSubject, String displayName, String currency, String status,
                           String availableBalance, String reservedBalance, Instant createdAt, Instant updatedAt) {
        static AccountResponse from(AccountView v) {
            return new AccountResponse(v.id(), v.ownerSubject(), v.displayName(), v.currency(), v.status(),
                    v.availableBalance(), v.reservedBalance(), v.createdAt(), v.updatedAt());
        }
    }

    /** {@code depositId} is client-generated: retrying the same deposit is safe (idempotent). */
    record DepositRequest(@NotNull UUID depositId,
                          @NotBlank @Pattern(regexp = "^\\d{1,15}(\\.\\d{1,4})?$") String amount,
                          @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Open an account owned by the caller")
    ResponseEntity<AccountResponse> open(Actor actor, @Valid @RequestBody OpenAccountRequest request) {
        AccountView view = openAccount.open(new OpenAccountCommand(actor, request.displayName(), request.currency()));
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + view.id())).body(AccountResponse.from(view));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get an account owned by the caller")
    AccountResponse get(Actor actor, @PathVariable UUID accountId) {
        return AccountResponse.from(getAccount.get(actor, new AccountId(accountId)));
    }

    @PostMapping(path = "/{accountId}/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Credit funds to an account (treasury; requires funds:deposit)")
    ResponseEntity<AccountResponse> deposit(Actor actor, @PathVariable UUID accountId,
                                            @Valid @RequestBody DepositRequest request) {
        DepositResult result = depositFunds.deposit(new DepositCommand(actor, new AccountId(accountId),
                request.depositId(), Money.of(request.amount(), request.currency())));
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(AccountResponse.from(result.account()));
    }

    @PostMapping("/{accountId}/freeze")
    @Operation(summary = "Place a compliance hold on an account (requires accounts:admin)")
    AccountResponse freeze(Actor actor, @PathVariable UUID accountId) {
        return AccountResponse.from(freezeAccount.freeze(actor, new AccountId(accountId)));
    }
}
