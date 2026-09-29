#!/bin/sh
# PayFlow Kafka ACL matrix (Zero Trust: least privilege per service identity).
#
# Production intent: every service authenticates with its own principal (SASL/SCRAM or mTLS, from the platform
# secret store) and gets exactly these permissions. Local docker-compose runs PLAINTEXT without an authorizer
# (documented dev relaxation). The matrix below is the reviewed contract and is applied by the platform WP.
#
# Usage (against a SASL-enabled cluster with StandardAuthorizer):
#   KAFKA_BIN=/opt/kafka/bin BOOTSTRAP=broker:9093 ADMIN_CONFIG=admin.properties ./acl-matrix.sh
set -eu
ACL="${KAFKA_BIN:-/opt/kafka/bin}/kafka-acls.sh --bootstrap-server ${BOOTSTRAP:?} --command-config ${ADMIN_CONFIG:?}"

allow_write() { $ACL --add --allow-principal "User:$1" --operation Write --operation Describe --topic "$2"; }
# Groups are PREFIXED: Spring's retry/DLT consumers use derived groups (<group>-<group>-retry-N, ...-dlt). A literal
# grant denies them silently (found in WP-03, failure test F-03).
allow_read()  { $ACL --add --allow-principal "User:$1" --operation Read --operation Describe --topic "$2";
                $ACL --add --allow-principal "User:$1" --operation Read --operation Describe \
                     --resource-pattern-type prefixed --group "$3"; }
# Retry/DLT topics are per consumer group: <topic>-<group>-retry-N and <topic>-<group>-dlt (prefixed ACL).
allow_retry() { $ACL --add --allow-principal "User:$1" --operation Read --operation Write --operation Describe \
                     --resource-pattern-type prefixed --topic "$2-$3-"; }

# payment-service: saga orchestrator; the ONLY writer of commands and payment.events.
allow_write payment-service payment.events
allow_write payment-service fraud.commands
allow_write payment-service funds.commands
allow_write payment-service settlement.commands
allow_read  payment-service fraud.events        payment-service
allow_read  payment-service funds.events        payment-service
allow_read  payment-service settlement.events   payment-service
for t in fraud.events funds.events settlement.events; do allow_retry payment-service "$t" payment-service; done

# fraud-service: reads its commands (contains personal data), writes only fraud.events.
allow_read  fraud-service fraud.commands fraud-service
allow_write fraud-service fraud.events
allow_retry fraud-service fraud.commands fraud-service

# account-service (funds): reads funds.commands, writes only funds.events.
allow_read  account-service funds.commands account-service
allow_write account-service funds.events
allow_retry account-service funds.commands account-service

# settlement-service: reads settlement.commands, writes only settlement.events.
allow_read  settlement-service settlement.commands settlement-service
allow_write settlement-service settlement.events
allow_retry settlement-service settlement.commands settlement-service

# ledger-service: read-only follower of funds.events; writes nothing to the backbone.
allow_read  ledger-service funds.events ledger-service
allow_retry ledger-service funds.events ledger-service

# Explicitly NOT granted (examples): fraud-service → funds.*; ledger-service → any Write; anyone but payment-service
# → *.commands. With the authorizer's default deny, absence of a grant is a denial.
