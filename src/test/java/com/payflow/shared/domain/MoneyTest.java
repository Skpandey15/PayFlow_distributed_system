package com.payflow.shared.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @ParameterizedTest(name = "{0} {1} normalises to {2}")
    @CsvSource({
            "10,     USD, 10.00",
            "10.5,   USD, 10.50",
            "10.50,  USD, 10.50",
            "1000,   JPY, 1000",
            "1.234,  KWD, 1.234",
            "1E+3,   EUR, 1000.00",
            "10.5000,USD, 10.50"})
    void normalisesToTheCurrencyMinorUnitScale(String amount, String currency, String expected) {
        assertThat(Money.of(amount, currency).amount()).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "{0} {1} is rejected, not rounded")
    @CsvSource({"10.001, USD", "100.5, JPY", "1.2345, KWD"})
    void rejectsMorePrecisionThanTheCurrencyAllowsInsteadOfRounding(String amount, String currency) {
        assertThatThrownBy(() -> Money.of(amount, currency))
                .isInstanceOf(DomainRuleViolationException.class)
                .extracting("code").isEqualTo("MONEY_PRECISION_EXCEEDED");
    }

    @Test
    void equalityIsScaleInsensitiveAfterNormalisation() {
        assertThat(Money.of("10.5", "USD")).isEqualTo(Money.of("10.50", "USD"));
    }

    @Test
    void arithmeticIsExact() {
        Money tenCents = Money.of("0.10", "USD");
        Money twentyCents = Money.of("0.20", "USD");
        assertThat(tenCents.plus(twentyCents)).isEqualTo(Money.of("0.30", "USD"));
        assertThat(tenCents.minus(twentyCents)).isEqualTo(Money.of("-0.10", "USD"));
    }

    @Test
    void refusesToCombineDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "USD").plus(Money.of("1", "EUR")))
                .isInstanceOf(DomainRuleViolationException.class)
                .extracting("code").isEqualTo("MONEY_CURRENCY_MISMATCH");
    }

    @Test
    void rejectsUnknownAndNonMonetaryCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "ABC")).extracting("code").isEqualTo("MONEY_UNKNOWN_CURRENCY");
        // XAU (gold) has no minor unit (-1 fraction digits) and cannot be a payment currency.
        assertThatThrownBy(() -> Money.of("1", "XAU")).extracting("code").isEqualTo("MONEY_UNSUPPORTED_CURRENCY");
    }

    @Test
    void rejectsAmountsBeyondStorageRange() {
        assertThat(Money.of("999999999999999.99", "USD").amount()).hasScaleOf(2);
        assertThatThrownBy(() -> Money.of("1000000000000000", "USD"))
                .extracting("code").isEqualTo("MONEY_OUT_OF_RANGE");
    }

    @Test
    void signPredicates() {
        assertThat(Money.of("0.01", "USD").isPositive()).isTrue();
        assertThat(Money.zero(Money.currency("USD")).isZero()).isTrue();
        assertThat(Money.of("-0.01", "USD").isNegative()).isTrue();
        assertThat(Money.of("5", "USD").isGreaterThan(Money.of("4.99", "USD"))).isTrue();
    }
}
