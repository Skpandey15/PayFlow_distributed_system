#!/usr/bin/env bash
# Runs one k6 scenario against the local compose stack and produces an evidence folder:
#   performance/results/<run-id>/{k6-summary.json, docker-stats.csv, report.md, report.json}
# usage: ENV_FILE=.env performance/scripts/run.sh <scenario> [label]   (extra env: RATE_SCALE, DURATION, PERF_USERS)
set -euo pipefail
SCENARIO=$1; LABEL=${2:-run}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${ENV_FILE:=$ROOT/.env}"
set -a; . "$ENV_FILE"; set +a
# Measurement integrity: never run concurrently with another load generator (it would contaminate server metrics).
if [ -n "$(docker ps -q --filter ancestor=grafana/k6:2.3.0)" ]; then
  echo "another k6 load generator is running; refusing to start (results would be contaminated)" >&2; exit 3
fi
RUN_ID="$(date +%Y%m%d-%H%M%S)-$SCENARIO-$LABEL"
OUT="$ROOT/performance/results/$RUN_ID"; mkdir -p "$OUT"
NETWORK=${PAYFLOW_NETWORK:-payflow_default}
win() { if command -v cygpath >/dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
prom() { curl -s --get localhost:9090/api/v1/query --data-urlencode "query=$1" | python -c "import sys,json; r=json.load(sys.stdin)['data']['result']; print(int(float(r[0]['value'][1])) if r else -1)"; }
# Quiet = every outbox drained and no saga in flight. Runs never start on (or end before) a previous run's backlog:
# otherwise completion percentiles would silently exclude payments still in flight (survivorship bias).
quiesce() {
  local deadline=$(( $(date +%s) + ${QUIESCE_TIMEOUT:-1800} ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    b=$(prom 'max(payflow_outbox_backlog)+0'); o=$(prom 'sum(max by (step) (payflow_saga_open{step!="MANUAL_REVIEW"}))+0')
    [ "$b" = 0 ] && [ "$o" = 0 ] && return 0
    echo "waiting for quiet system: outbox backlog=$b open sagas=$o" >&2; sleep 15
  done
  echo "system did not quiesce within ${QUIESCE_TIMEOUT:-1800}s" >&2; return 1
}
[ "${SKIP_PRE_QUIESCE:-false}" = true ] || quiesce || true

# Container resource sampler (docker stats), every 5 s, for the whole run.
( while true; do docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' 2>/dev/null \
    | sed "s/^/$(date +%s),/"; sleep 10; done ) > "$OUT/docker-stats.csv" &
SAMPLER=$!
trap 'kill $SAMPLER 2>/dev/null || true' EXIT

# Launch k6; if the Docker daemon refused the launch itself (no summary produced), wait for it and retry (max 3).
for launch in 1 2 3; do
MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" \
  -v "$(win "$ROOT/performance/k6"):/scripts:ro" -v "$(win "$OUT"):/out" \
  -e SCENARIO_NAME="$SCENARIO" -e K6_OUT_DIR=/out -e PAYFLOW_API="${PAYFLOW_API:-http://payflow:8080}" \
  -e PAYFLOW_PERF_USER_PASSWORD -e PAYFLOW_TREASURY_CLIENT_SECRET \
  -e RATE_SCALE="${RATE_SCALE:-1}" -e DURATION="${DURATION:-}" -e PERF_USERS="${PERF_USERS:-20}" \
  -e PAYERS_PER_USER="${PAYERS_PER_USER:-5}" -e READ_RATIO="${READ_RATIO:-0.5}" \
  grafana/k6:2.3.0 run --quiet "/scripts/scenarios/$SCENARIO.js" 2>>"$OUT/k6-stderr.txt" | tee "$OUT/k6-stdout.txt" || true
[ -f "$OUT/k6-summary.json" ] && break
echo "k6 launch $launch produced no summary; waiting for the docker daemon" >&2
for _ in $(seq 1 40); do docker version >/dev/null 2>&1 && break; sleep 15; done
done

# Keep measuring until every accepted payment has finished: completion latency and backlog drain are part of the
# run. The drain time itself is evidence (how long the system needed to catch up after the load stopped).
LOAD_END=$(date +%s)
sleep 20
quiesce || true
sleep 10
DRAIN=$(( $(date +%s) - LOAD_END ))
kill $SAMPLER 2>/dev/null || true
python "$ROOT/performance/scripts/report.py" "$OUT" --drain "$DRAIN"
echo "evidence: $OUT"
