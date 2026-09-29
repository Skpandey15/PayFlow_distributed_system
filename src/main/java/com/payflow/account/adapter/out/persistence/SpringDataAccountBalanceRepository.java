package com.payflow.account.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAccountBalanceRepository extends JpaRepository<AccountBalanceJpaEntity, UUID> {

    /** {@code SELECT … FOR UPDATE}: the row lock is held until the enclosing transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalanceJpaEntity b where b.accountId = :accountId")
    Optional<AccountBalanceJpaEntity> findForUpdate(@Param("accountId") UUID accountId);
}
