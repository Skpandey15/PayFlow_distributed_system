package com.payflow.archfixture.domain;

import org.springframework.util.Assert;

/**
 * Deliberate violation fixture: a "domain" class that depends on Spring and uses double for money.
 * (Not a Spring bean; it only exists for ArchitectureRulesDetectViolationsTest.)
 */
public class LeakyDomainObject {

    double amount;

    void validate() {
        Assert.isTrue(amount > 0, "amount must be positive");
    }
}
