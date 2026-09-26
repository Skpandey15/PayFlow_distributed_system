package com.payflow.payment.domain;

import com.payflow.shared.domain.Identifiers;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "value");
    }

    public static PaymentId newId() {
        return new PaymentId(Identifiers.timeOrderedUuid());
    }

    public static PaymentId of(String value) {
        return new PaymentId(Identifiers.parse(value, "payment id"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
