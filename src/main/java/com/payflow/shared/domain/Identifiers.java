package com.payflow.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Identifier generation for aggregates.
 *
 * <p>Uses RFC 9562 UUIDv7 (48-bit Unix-millisecond prefix + random bits). Ids are globally unique
 * without coordination, so the domain can assign them before persistence, and they are roughly
 * time-ordered, which keeps PostgreSQL B-tree primary-key inserts append-mostly instead of the random
 * page splits caused by UUIDv4.
 */
public final class Identifiers {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Identifiers() {
    }

    public static UUID timeOrderedUuid() {
        long millis = System.currentTimeMillis();
        long randA = RANDOM.nextLong();
        long randB = RANDOM.nextLong();
        long msb = (millis << 16) | 0x7000L | (randA & 0x0FFFL);
        long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    public static UUID parse(String value, String what) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainRuleViolationException("INVALID_IDENTIFIER", "Malformed " + what + ": " + value);
        }
    }
}
