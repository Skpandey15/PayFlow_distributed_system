package com.payflow.account.application.port.in;

import com.payflow.shared.application.Actor;

public interface OpenAccountUseCase {

    AccountView open(OpenAccountCommand command);

    record OpenAccountCommand(Actor actor, String displayName, String currencyCode) {
    }
}
