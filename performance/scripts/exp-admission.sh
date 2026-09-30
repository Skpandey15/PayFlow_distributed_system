#!/usr/bin/env bash
# Review P-2 experiment: one admission variant through the overload scenarios, each on a fresh stack (reset, 90 s warm-up).
# The variant is whatever PAYFLOW_ADMISSION_* environment the caller exports; <label> names the runs.
#   ENV_FILE=.env PAYFLOW_ADMISSION_MAX_IN_FLIGHT=500 performance/scripts/exp-admission.sh inflight500 burst stress
set -uo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
LABEL=$1; shift
G='accepted_per_s|payments_throttled|api_post_p99_ms|saga_p99_ms|saga_p50_ms|drain_seconds|hikari_pending_max|gc_pause_max|process_cpu_max|consumer_lag_max_top5'
for SCENARIO in "$@"; do
  bash "$DIR/reset-stack.sh" | tail -1
  SKIP_PRE_QUIESCE=true DURATION=90s bash "$DIR/run.sh" smoke "warmup-$LABEL" >/dev/null 2>&1
  echo "##### $SCENARIO-$LABEL"
  QUIESCE_TIMEOUT=900 bash "$DIR/run.sh" "$SCENARIO" "$LABEL" 2>&1 | grep -E "$G" | cut -c1-200
done
