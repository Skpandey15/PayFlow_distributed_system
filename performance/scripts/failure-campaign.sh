#!/usr/bin/env bash
# WP-03 live failure campaign against the compose lab. Each scenario = steady load + real fault + recovery +
# data-safety check (failure-scenario.sh). IDs follow the WP-03 brief (F-01 … F-24); scenarios covered by
# integration tests or by performance runs are referenced in WP-03-FAILURE-MATRIX.md instead of repeated here.
#   ENV_FILE=.env performance/scripts/failure-campaign.sh [scenario-ids...]
set -uo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
RAIL=http://localhost:${PAYFLOW_RAIL_HOST_PORT:-8090}
faults() { printf "curl -s -X POST $RAIL/admin/faults -H 'Content-Type: application/json' -d '%s'" "$1"; }
HEAL_RAIL="curl -s -X DELETE $RAIL/admin/faults"
run() { echo "=== $(date -u +%T) $1"; bash "$DIR/failure-scenario.sh" "$@" | grep -E "scenario=|time_to_quiet|acceptance_by_status|saga_outcomes|completion_p99|alerts_fired|open_mismatches|double_|settled_without|dead_lettered|max_outbox_age|rail_outcomes|recovery_" ; }

want() { [ $# -eq 0 ] || [[ " ${SELECTED[*]} " == *" $1 "* ]]; }
SELECTED=("$@")

want F01 && FAULT_SECONDS=90 run F01-kafka-unavailable "docker stop payflow-kafka-1" "docker start payflow-kafka-1"
want F02 && FAULT_SECONDS=90 run F02-kafka-degraded "docker update --cpus 0.15 payflow-kafka-1" "docker update --cpus 2 payflow-kafka-1"
want F03 && FAULT_SECONDS=45 run F03-postgres-unavailable "docker pause payflow-postgres-1" "docker unpause payflow-postgres-1"
want F04 && FAULT_SECONDS=60 run F04-postgres-slow-pool-exhaustion "docker update --cpus 0.1 payflow-postgres-1" "docker update --cpus 2 payflow-postgres-1"
want F05 && FAULT_SECONDS=150 run F05-mongodb-unavailable "docker stop payflow-mongo-1" "docker start payflow-mongo-1"
want F06 && FAULT_SECONDS=90 run F06-settlement-unavailable "$(faults '{"errorRate":1.0,"errorMode":"UNAVAILABLE_503"}')" "$HEAL_RAIL"
want F07 && FAULT_SECONDS=90 run F07-settlement-slow-bulkhead "$(faults '{"latencyMs":1700,"jitterMs":200}')" "$HEAL_RAIL"
want F08 && FAULT_SECONDS=90 run F08-settlement-timeout-unknown-outcome "$(faults '{"errorRate":0.3,"errorMode":"TIMEOUT_AFTER_ACCEPT"}')" "$HEAL_RAIL"
want F11 && LOAD_DURATION=4m FAULT_SECONDS=120 run F11-retry-storm-attempt "$(faults '{"errorRate":1.0,"errorMode":"RESET"}')" "$HEAL_RAIL"
want F14 && FAULT_SECONDS=20 run F14-consumer-crash-sigkill "docker kill -s KILL payflow-payflow-1" "docker start payflow-payflow-1"
want F15 && FAULT_SECONDS=90 run F15-rebalance-extra-instance \
  "docker compose --env-file \"$ENV_FILE\" run -d --no-deps --name payflow-payflow-2 -e SPRING_KAFKA_LISTENER_CONCURRENCY=6 payflow; sleep 45; docker exec payflow-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9094 --describe --group account-service --members" \
  "docker exec payflow-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9094 --describe --group account-service --members; docker rm -f payflow-payflow-2"
want F21 && FAULT_SECONDS=15 run F21-graceful-shutdown-sigterm "docker stop -t 45 payflow-payflow-1" "docker start payflow-payflow-1"
want F22 && FAULT_SECONDS=5 run F22-restart "docker restart -t 45 payflow-payflow-1" "true"
echo "=== $(date -u +%T) failure campaign done"
