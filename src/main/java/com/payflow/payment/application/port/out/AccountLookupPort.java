package com.payflow.payment.application.port.out;

import com.payflow.shared.domain.AccountId;

import java.util.Currency;
import java.util.Optional;

/**
 * What the Payment context needs to know about accounts, expressed in Payment's own language.
 * The adapter is an Anti-Corruption Layer over the Account context's published API.
 */
public interface AccountLookupPort {

    Optional<PaymentParty> find(AccountId accountId);

    record PaymentParty(AccountId accountId, String ownerSubject, Currency currency, boolean canTransact) {

        public boolean isOwnedBy(String subject) {
            return ownerSubject.equals(subject);
        }
    }
}
