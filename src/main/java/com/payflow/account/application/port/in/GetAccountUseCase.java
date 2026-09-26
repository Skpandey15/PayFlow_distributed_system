package com.payflow.account.application.port.in;

import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;

public interface GetAccountUseCase {

    /** Returns the account if it exists and is visible to the actor (owner or accounts:admin). */
    AccountView get(Actor actor, AccountId accountId);
}
