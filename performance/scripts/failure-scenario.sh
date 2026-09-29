#!/usr/bin/env bash
# One live failure experiment against the compose lab (WP-03 failure campaign):
#   1. steady load (k6 "degraded" scenario, default 20/s for LOAD_DURATION)
#   2. at INJECT_AT seconds: run the inject command; after FAULT_SECONDS: run the recover command
#   3. wait until quiet, then record: acceptance errors, completion, alerts that fired, recovery time, data safety
#      (reconciliation run + direct SQL invariants), into performance/results/failure-<name>-<ts>/summary.txt
#
#   ENV_FILE=.env performance/scripts/failure-scenario.sh <name> "<inject cmd>" "<recover cmd>"
set -uo pipefail
NAME=$1; INJECT=$2; RECOVER=$3
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${ENV_FILE:=$ROOT/.env}"
set -a; . "$ENV_FILE"; set +a
INJECT_AT=${INJECT_AT:-60}; FAULT_SECONDS=${FAULT_SECONDS:-60}; LOAD_DURATION=${LOAD_DURATION:-4m}
OUT="$ROOT/performance/results/failure-$NAME-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"
prom() { curl -s --get localhost:9090/api/v1/query --data-urlencode "query=$1" | python -c "import sys,json; r=json.load(sys.stdin)['data']['result']; print(json.dumps({','.join(v for k,v in sorted(x['metric'].items()) if k!='__name__') or 'value': round(float(x['value'][1]),3) for x in r}))"; }
psql() { docker exec -i payflow-postgres-1 psql -U "${POSTGRES_SUPERUSER:-postgres}" -d payflow -qtAX -c "$1"; }

T0=$(date +%s)
( DRAIN_SECONDS=0 QUIESCE_TIMEOUT=${QUIESCE_TIMEOUT:-900} DURATION=$LOAD_DURATION bash "$ROOT/performance/scripts/run.sh" degraded "fault-$NAME" > "$OUT/load.log" 2>&1 ) &
LOAD=$!
sleep "$INJECT_AT"
echo "inject  $(date -u +%T): $INJECT" | tee -a "$OUT/timeline.txt"
bash -c "$INJECT" >> "$OUT/timeline.txt" 2>&1
TI=$(date +%s)
sleep "$FAULT_SECONDS"
echo "recover $(date -u +%T): $RECOVER" | tee -a "$OUT/timeline.txt"
bash -c "$RECOVER" >> "$OUT/timeline.txt" 2>&1
TR=$(date +%s)
wait $LOAD
TQ=$(date +%s)
W="$(( TQ - T0 ))s"

# Data safety: reconciliation (all nine invariants) plus direct checks that no payment moved money twice.
docker exec payflow-payflow-1 true 2>/dev/null && TOKEN=$(curl -s "http://localhost:${PAYFLOW_KEYCLOAK_HOST_PORT:-8081}/realms/payflow/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=payflow-ops --data-urlencode "client_secret=$PAYFLOW_OPS_CLIENT_SECRET" \
  | python -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))")
curl -s -X POST "http://localhost:${PAYFLOW_API_HOST_PORT:-8080}/api/v1/ops/reconciliation/runs" -H "Authorization: Bearer ${TOKEN:-none}" > "$OUT/reconciliation-run.json"
{
  echo "scenario=$NAME inject_at=${INJECT_AT}s fault=${FAULT_SECONDS}s load=$LOAD_DURATION"
  echo "time_to_quiet_after_recover_s=$(( TQ - TR ))"
  echo "report=$(ls -d "$ROOT"/performance/results/*-degraded-fault-$NAME 2>/dev/null | tail -1)"
  echo "acceptance_by_status=$(prom "sum by (status) (increase(http_server_requests_seconds_count{uri=\"/api/v1/payments\",method=\"POST\"}[$W]))")"
  echo "shed_by_policy=$(prom "sum by (policy) (increase(payflow_traffic_rejected_total[$W]))")"
  echo "saga_outcomes=$(prom "sum by (outcome) (increase(payflow_saga_completion_seconds_count[$W]))")"
  echo "completion_p99_ms=$(prom "histogram_quantile(0.99, sum by (le) (increase(payflow_saga_completion_seconds_bucket{outcome=\"COMPLETED\"}[$W]))) * 1000")"
  echo "open_sagas_now=$(prom 'max by (step) (payflow_saga_open) > 0')"
  echo "recovery_actions=$(prom "sum by (action) (increase(payflow_saga_recovery_total[$W]))")"
  echo "recovery_held=$(prom "sum(increase(payflow_saga_recovery_held_total[$W]))")"
  echo "events_failed=$(prom "sum by (category) (increase(payflow_events_failed_total[$W]))")"
  echo "dead_lettered=$(prom "sum by (topic) (increase(payflow_events_dead_lettered_total[$W]))")"
  echo "rail_outcomes=$(prom "sum by (outcome) (increase(payflow_settlement_rail_calls_total[$W]))")"
  echo "max_outbox_age_s=$(prom "max(max_over_time(payflow_outbox_oldest_age_seconds[$W]))")"
  echo "alerts_fired=$(prom "count by (alertname) (max_over_time(ALERTS{alertstate=\"firing\"}[$W]))")"
  echo "reconciliation=$(cat "$OUT/reconciliation-run.json")"
  echo "open_mismatches=$(psql "select coalesce(string_agg(check_name||':'||cnt, ','), 'none') from (select check_name, count(*) cnt from reconciliation.mismatch where status='OPEN' group by 1) s")"
  echo "double_capture=$(psql "select count(*) from (select payment_id from account.funds_reservation where status='CAPTURED' group by payment_id having count(*)>1) d")"
  echo "double_journal=$(psql "select count(*) from (select reference from ledger.journal_entry where reference like 'payment:%:settlement' group by reference having count(*)>1) d")"
  echo "settled_without_rail_completion=$(psql "select count(*) from payment.payment p left join settlement.settlement s on s.payment_id=p.id and s.status='COMPLETED' where p.status='SETTLED' and s.id is null")"
} | tee "$OUT/summary.txt"
