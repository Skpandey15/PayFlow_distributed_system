package com.payflow.payment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "payment", name = "idempotency_record")
public class IdempotencyRecordJpaEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected IdempotencyRecordJpaEntity() {
    }

    IdempotencyRecordJpaEntity(Key key, String requestFingerprint, UUID paymentId, Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.requestFingerprint = requestFingerprint;
        this.paymentId = paymentId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    Key getKey() {
        return key;
    }

    String getRequestFingerprint() {
        return requestFingerprint;
    }

    UUID getPaymentId() {
        return paymentId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "client_id", nullable = false, updatable = false)
        private String clientId;

        @Column(name = "idempotency_key", nullable = false, updatable = false)
        private String idempotencyKey;

        protected Key() {
        }

        Key(String clientId, String idempotencyKey) {
            this.clientId = clientId;
            this.idempotencyKey = idempotencyKey;
        }

        String getClientId() {
            return clientId;
        }

        String getIdempotencyKey() {
            return idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && clientId.equals(other.clientId) && idempotencyKey.equals(other.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clientId, idempotencyKey);
        }
    }
}
