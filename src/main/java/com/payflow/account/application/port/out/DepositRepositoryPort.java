package com.payflow.account.application.port.out;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepositoryPort {

    Optional<DepositRecord> find(UUID depositId);

    void add(DepositRecord deposit);

    record DepositRecord(UUID depositId, AccountId accountId, Money amount, String createdBy, Instant createdAt) {
    }
}
