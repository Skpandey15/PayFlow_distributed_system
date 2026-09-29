package com.payflow.account.application.port.out;

import com.payflow.account.domain.FundsReservation;

import java.util.Optional;
import java.util.UUID;

public interface FundsReservationRepositoryPort {

    Optional<FundsReservation> findByPaymentId(UUID paymentId);

    void add(FundsReservation reservation);

    void update(FundsReservation reservation);
}
