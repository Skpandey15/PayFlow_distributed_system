package com.payflow.payment.adapter.out.account;

import com.payflow.account.application.port.in.LookupAccountUseCase;
import com.payflow.payment.application.port.out.AccountLookupPort;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Anti-Corruption Layer: translates the Account context's published model into Payment's own
 * {@link PaymentParty}. This adapter is the only Payment class that knows the Account context exists.
 * Extracting Account into a service means replacing this adapter with an HTTP/gRPC client; the use cases
 * do not change.
 */
@Component
class AccountContextAdapter implements AccountLookupPort {

    private final LookupAccountUseCase accounts;

    AccountContextAdapter(LookupAccountUseCase accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<PaymentParty> find(AccountId accountId) {
        return accounts.findAccount(accountId).map(a -> new PaymentParty(a.id(), a.ownerSubject(),
                Money.currency(a.currencyCode()), a.active()));
    }
}
