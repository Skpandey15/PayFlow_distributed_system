package com.payflow.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount in a single ISO-4217 currency.
 *
 * <p>Precision policy (see docs/architecture/DATA-ARCHITECTURE.md):
 * <ul>
 *   <li>The amount is always normalised to the currency's minor-unit scale (USD=2, JPY=0, KWD=3).</li>
 *   <li>Input with more fractional digits than the currency allows is <b>rejected</b>, never rounded:
 *       silently rounding a client-supplied amount changes what the payer agreed to pay.</li>
 *   <li>At most 15 integer digits, matching the {@code NUMERIC(19,4)} storage type.</li>
 *   <li>Arithmetic is only defined between amounts of the same currency; there is no implicit FX.</li>
 * </ul>
 * {@code double}/{@code float} are never used for money (enforced by ArchUnit).
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int MAX_INTEGER_DIGITS = 15;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int minorUnits = currency.getDefaultFractionDigits();
        if (minorUnits < 0) {
            throw new DomainRuleViolationException("MONEY_UNSUPPORTED_CURRENCY",
                    "Currency %s has no minor unit and cannot be used for payments".formatted(currency));
        }
        if (amount.stripTrailingZeros().scale() > minorUnits) {
            throw new DomainRuleViolationException("MONEY_PRECISION_EXCEEDED",
                    "Amount %s has more than %d fractional digits allowed for %s"
                            .formatted(amount.toPlainString(), minorUnits, currency));
        }
        amount = amount.setScale(minorUnits, RoundingMode.UNNECESSARY);
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new DomainRuleViolationException("MONEY_OUT_OF_RANGE",
                    "Amount %s exceeds the supported range".formatted(amount.toPlainString()));
        }
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), currency(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Currency currency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainRuleViolationException("MONEY_UNKNOWN_CURRENCY",
                    "Unknown ISO-4217 currency code: " + currencyCode);
        }
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean hasCurrency(Currency expected) {
        return currency.equals(expected);
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new DomainRuleViolationException("MONEY_CURRENCY_MISMATCH",
                    "Cannot combine %s with %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }
}
