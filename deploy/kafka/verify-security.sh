#!/usr/bin/env bash
# Proves the Kafka Zero Trust controls against the running compose broker (evidence for ADR-023 / WP-03 review).
# Every probe runs from a separate client container on the compose network, like any other network client.
#   ENV_FILE=.env deploy/kafka/verify-security.sh
set -uo pipefail
: "${ENV_FILE:=.env}"
set -a; . "$ENV_FILE"; set +a
NET=${PAYFLOW_NETWORK:-payflow_default}
BS=kafka:29092
pass=0; fail=0

client() { # $1 = client properties (may be empty), rest = command
  local props=$1; shift
  MSYS_NO_PATHCONV=1 docker run --rm --network "$NET" -e PROPS="$props" --entrypoint bash apache/kafka:4.2.0 -c \
    "printf '%b' \"\$PROPS\" > /tmp/c.properties; $* 2>&1" | tr -d '\r'
}
sasl() { printf 'security.protocol=SASL_PLAINTEXT\\nsasl.mechanism=SCRAM-SHA-512\\nsasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username=\\"%s\\" password=\\"%s\\";\\n' "$1" "$2"; }
refute() { # $1 = description, $2 = regex that must NOT appear, $3 = output
  if printf '%s' "$3" | grep -Eq "$2"; then echo "FAIL  $1"; printf '%s\n' "$3" | tail -3; fail=$((fail+1)); else echo "PASS  $1"; pass=$((pass+1)); fi
}
expect() { # $1 = description, $2 = regex expected in output, $3 = output
  if printf '%s' "$3" | grep -Eq "$2"; then echo "PASS  $1"; pass=$((pass+1)); else echo "FAIL  $1"; printf '%s\n' "$3" | tail -3; fail=$((fail+1)); fi
}
T="timeout 25"
PROD="/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server $BS --producer.config /tmp/c.properties"
CONS="/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server $BS --consumer.config /tmp/c.properties --timeout-ms 8000 --max-messages 1 --from-beginning"

out=$(client "security.protocol=PLAINTEXT\n" "$T /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS --command-config /tmp/c.properties --list")
# A PLAINTEXT client cannot complete the handshake on a SASL listener: it times out without learning anything.
refute "unauthenticated (PLAINTEXT) client learns no topic names" "payment[.]events|funds[.]|fraud[.]" "$out"

out=$(client "$(sasl payflow-app wrong-password)" "$T /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS --command-config /tmp/c.properties --list")
expect "wrong SCRAM password is rejected" "SaslAuthenticationException|Authentication failed" "$out"

out=$(client "$(sasl acl-probe "$PAYFLOW_KAFKA_SERVICE_PASSWORD")" "echo probe | $T $PROD --topic payment.events")
expect "authenticated principal without grants cannot write payment.events (deny by default)" "TopicAuthorizationException|Not authorized" "$out"

out=$(client "$(sasl ledger-service "$PAYFLOW_KAFKA_SERVICE_PASSWORD")" "$T $CONS --topic fraud.commands --group ledger-service")
expect "ledger-service cannot read fraud.commands (personal data it has no need for)" "TopicAuthorizationException|Not authorized" "$out"

out=$(client "$(sasl fraud-service "$PAYFLOW_KAFKA_SERVICE_PASSWORD")" "echo forged | $T $PROD --topic funds.events")
expect "fraud-service cannot forge funds.events (single writer per topic)" "TopicAuthorizationException|Not authorized" "$out"

out=$(client "$(sasl ledger-service "$PAYFLOW_KAFKA_SERVICE_PASSWORD")" "$T $CONS --topic funds.events --group ledger-service")
expect "ledger-service CAN read funds.events (its granted stream)" "^\{|Processed a total of|TimeoutException" "$out"

out=$(client "$(sasl payflow-app "$PAYFLOW_KAFKA_APP_PASSWORD")" "$T /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS --command-config /tmp/c.properties --create --topic rogue.topic --partitions 1 --replication-factor 1")
expect "payflow-app cannot create topics outside its namespaces" "TopicAuthorizationException|Not authorized|Authorization failed" "$out"

out=$(client "$(sasl payflow-app "$PAYFLOW_KAFKA_APP_PASSWORD")" "$T /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS --command-config /tmp/c.properties --list")
expect "payflow-app sees its own topics" "payment.events" "$out"

echo "---- $pass passed, $fail failed"
[ "$fail" -eq 0 ]
