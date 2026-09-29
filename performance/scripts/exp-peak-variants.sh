#!/usr/bin/env bash
# Two fair peak experiments on a freshly reset stack each (TUNING-RESULTS §5):
#   A. one instance, SerialGC (same build) - attributes the G1 cost at peak
#   B. two instances, G1 (the production shape: minReplicas 2) - does completion capacity scale out?
set -uo pipefail
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd)
: "${ENV_FILE:?}"
G='^evidence|^\| (accepted_per_s|api_post_p99_ms|saga_p99_ms|dropped_iterations|gc_pause_max_ms|drain_seconds_after_load|outbox_publish_delay_p99_ms) '
cd "$ROOT"

echo "=== $(date -u +%T) A: peak, SerialGC, fresh stack"
PAYFLOW_JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" bash "$DIR/reset-stack.sh" | tail -1
SKIP_PRE_QUIESCE=true DURATION=90s bash "$DIR/run.sh" smoke warmup-exp >/dev/null 2>&1
docker exec payflow-payflow-1 env | grep JAVA_TOOL
QUIESCE_TIMEOUT=900 bash "$DIR/run.sh" peak serialgc-fresh 2>&1 | grep -E "$G"

echo "=== $(date -u +%T) B: peak, two G1 instances, fresh stack"
bash "$DIR/reset-stack.sh" | tail -1
docker compose --env-file "$ENV_FILE" run -d --no-deps --name payflow-payflow-2 payflow >/dev/null
for _ in $(seq 1 60); do docker exec payflow-payflow-2 wget -qO- http://localhost:8080/actuator/health/readiness 2>/dev/null | grep -q UP && break; sleep 5; done
docker exec payflow-payflow-2 env | grep JAVA_TOOL
export PAYFLOW_API="http://payflow-payflow-1:8080,http://payflow-payflow-2:8080"
SKIP_PRE_QUIESCE=true DURATION=90s bash "$DIR/run.sh" smoke warmup-exp >/dev/null 2>&1
QUIESCE_TIMEOUT=900 bash "$DIR/run.sh" peak two-instances 2>&1 | grep -E "$G"
docker exec payflow-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9094 --describe --group payment-service --members 2>/dev/null
docker rm -f payflow-payflow-2 >/dev/null
echo "=== $(date -u +%T) done"
