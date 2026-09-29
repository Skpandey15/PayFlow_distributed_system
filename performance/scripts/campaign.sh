#!/usr/bin/env bash
# Runs a list of scenarios back to back, each starting from a quiet system, after one JIT warm-up run.
#   ENV_FILE=.env performance/scripts/campaign.sh <label> normal peak burst hot-account stress soak
set -uo pipefail
LABEL=$1; shift
DIR=$(cd "$(dirname "$0")" && pwd)
DURATION=90s bash "$DIR/run.sh" smoke "warmup-$LABEL" > /dev/null 2>&1
for s in "$@"; do
  echo "=== $(date -u +%FT%TZ) $s ($LABEL)"
  bash "$DIR/run.sh" "$s" "$LABEL" 2>&1 | grep -E "^evidence|did not quiesce|^\| (accepted_per_s|api_post_p99_ms|saga_p99_ms|outbox_publish_delay_p99_ms|dropped_iterations|drain_seconds_after_load) "
done
echo "=== $(date -u +%FT%TZ) campaign $LABEL done"
