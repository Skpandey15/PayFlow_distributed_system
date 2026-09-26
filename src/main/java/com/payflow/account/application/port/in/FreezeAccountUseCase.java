package com.payflow.account.application.port.in;

import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;

public interface FreezeAccountUseCase {

    /** Compliance hold: frozen accounts cannot take part in new payments or authorizations. */
    AccountView freeze(Actor actor, AccountId accountId);
}
