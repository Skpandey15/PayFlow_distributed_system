package com.payflow.ledger.adapter.in.web;

import com.payflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.payflow.ledger.application.port.in.GetAccountBalanceUseCase.BalanceView;
import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/ledger", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Ledger")
class LedgerController {

    private final GetAccountBalanceUseCase balances;

    LedgerController(GetAccountBalanceUseCase balances) {
        this.balances = balances;
    }

    record BalanceResponse(UUID accountId, String amount, String currency) {
    }

    @GetMapping("/accounts/{accountId}/balance")
    @Operation(summary = "Ledger-derived balance of an account (requires ledger:read)")
    BalanceResponse balance(Actor actor, @PathVariable UUID accountId,
                            @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency) {
        BalanceView view = balances.balance(actor, new AccountId(accountId), currency);
        return new BalanceResponse(view.accountId().value(), view.balance().amount().toPlainString(),
                view.balance().currencyCode());
    }
}
