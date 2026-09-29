#!/usr/bin/env bash
# Runs INSIDE the Kafka container once the broker is up (see secure-entrypoint.sh). Idempotent.
#
# It talks to the broker over the loopback-only LOCAL listener (127.0.0.1:9094, PLAINTEXT, principal ANONYMOUS =
# super user). That listener is unreachable from outside the container; it exists only because KRaft SCRAM
# credentials must be created by someone before anyone can authenticate (break-glass bootstrap, documented in
# ADR-023). Every other listener requires SASL/SCRAM and is subject to ACLs with deny-by-default.
set -euo pipefail
BIN=/opt/kafka/bin
BS=127.0.0.1:9094
: "${PAYFLOW_KAFKA_APP_PASSWORD:?}" "${PAYFLOW_KAFKA_SERVICE_PASSWORD:?}"

until $BIN/kafka-broker-api-versions.sh --bootstrap-server $BS >/dev/null 2>&1; do sleep 2; done

scram() { $BIN/kafka-configs.sh --bootstrap-server $BS --alter --entity-type users --entity-name "$1" \
            --add-config "SCRAM-SHA-512=[iterations=8192,password=$2]" >/dev/null; }

# 1. Identities. payflow-app is the modular monolith's single runtime identity (K1: one process cannot hold several
#    least-privilege identities). The per-service principals below are the post-extraction identities; they are
#    provisioned with the reviewed ACL matrix now so least privilege is enforced and verifiable today.
scram payflow-app "$PAYFLOW_KAFKA_APP_PASSWORD"
for svc in payment-service fraud-service account-service settlement-service ledger-service acl-probe; do
  scram "$svc" "$PAYFLOW_KAFKA_SERVICE_PASSWORD"
done

ACL="$BIN/kafka-acls.sh --bootstrap-server $BS"
# 2. payflow-app: only PayFlow's own topic namespaces (incl. its retry/DLT topics), its own consumer groups.
#    CREATE is scoped to those prefixes (the app declares its topics); no cluster-level ALTER, no other topics.
for prefix in payment. fraud. funds. settlement.; do
  $ACL --add --allow-principal User:payflow-app --operation Create --operation Describe --operation Read \
       --operation Write --resource-pattern-type prefixed --topic "$prefix" >/dev/null
done
# PREFIXED on purpose: Spring's non-blocking retry derives the retry/DLT consumer groups from the main group
# (e.g. ledger-service-ledger-service-retry-0). A literal grant silently denied every retry and DLT consumer; found in
# WP-03 by reconciliation (a lost ledger posting) during failure test F-03.
for group in payment-service account-service fraud-service settlement-service ledger-service; do
  $ACL --add --allow-principal User:payflow-app --operation Read --operation Describe --resource-pattern-type prefixed --group "$group" >/dev/null
done

# 3. Per-service principals: the WP-02 reviewed matrix, applied for real.
touch /tmp/local-admin.properties
KAFKA_BIN=$BIN BOOTSTRAP=$BS ADMIN_CONFIG=/tmp/local-admin.properties sh /opt/payflow/acl-matrix.sh >/dev/null

# acl-probe: authenticated but granted nothing. Used by verify-security.sh to prove deny-by-default.
echo "kafka security bootstrap complete: $($ACL --list 2>/dev/null | grep -c 'principal=User:') ACL entries"
