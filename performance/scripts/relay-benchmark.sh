#!/usr/bin/env bash
# Isolated outbox-relay throughput benchmark (K2, ADR-019). Inserts N committed-but-unpublished rows into
# payment.outbox_event in one statement and measures how fast the running relay drains them, from the rows' own
# published_at timestamps. Rows go to payment.events, which has no internal consumer, so no saga or consumer work
# is triggered: this measures the relay + broker path only.
#   ENV_FILE=.env performance/scripts/relay-benchmark.sh [rows] [label]
set -euo pipefail
N=${1:-20000}; LABEL=${2:-run}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${ENV_FILE:=$ROOT/.env}"
set -a; . "$ENV_FILE"; set +a
PSQL=(docker exec -i payflow-postgres-1 psql -U "${POSTGRES_SUPERUSER:-postgres}" -d payflow -qtAX -v ON_ERROR_STOP=1)
MARK="relay-bench-$(date +%s)"

"${PSQL[@]}" <<SQL
INSERT INTO payment.outbox_event (event_id, aggregate_type, aggregate_id, topic, message_key, event_type,
                                  event_version, envelope, correlation_id, traceparent, created_at)
SELECT gen_random_uuid(), 'Payment', k::text, 'payment.events', k::text, 'PaymentCreated', 1,
       jsonb_build_object('eventId', gen_random_uuid(), 'eventType', 'PaymentCreated', 'eventVersion', 1,
                          'producer', 'payment-service', 'aggregateType', 'Payment', 'aggregateId', k::text,
                          'occurredAt', now(), 'correlationId', '$MARK', 'payload',
                          jsonb_build_object('paymentId', k::text, 'amount', '10.00', 'currency', 'USD')),
       '$MARK', NULL, clock_timestamp()
  FROM (SELECT gen_random_uuid() AS k FROM generate_series(1, $N)) s;
SQL
START=$(date +%s)
until [ "$("${PSQL[@]}" -c "select count(*) from payment.outbox_event where correlation_id = '$MARK' and published_at is null")" = 0 ]; do
  sleep 1
  [ $(( $(date +%s) - START )) -gt 1800 ] && { echo "benchmark timed out" >&2; break; }
done
RESULT=$("${PSQL[@]}" -F' ' -c "select count(*), extract(epoch from max(published_at) - min(published_at)),
  extract(epoch from max(published_at) - min(created_at))
  from payment.outbox_event where correlation_id = '$MARK'")
read -r COUNT SPAN TOTAL <<<"$RESULT"
RATE=$(python -c "print(round($COUNT / max($SPAN, 0.001)))")
OUT="$ROOT/performance/results/relay-benchmark-$LABEL-$(date +%Y%m%d-%H%M%S).txt"
printf 'rows=%s publish_span_s=%.2f drain_total_s=%.2f events_per_s=%s label=%s\n' "$COUNT" "$SPAN" "$TOTAL" "$RATE" "$LABEL" | tee "$OUT"
