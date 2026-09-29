#!/usr/bin/env bash
# Like campaign.sh, but every scenario starts from a freshly reset stack (reset-stack.sh) and the drain phase is
# bounded (QUIESCE_TIMEOUT, default 300 s). For systems that cannot drain an overload run in reasonable time: the
# report then states how much work was still unfinished when the window closed (that IS the finding).
#   ENV_FILE=.env performance/scripts/campaign-reset.sh <label> peak burst hot-account stress
set -uo pipefail
LABEL=$1; shift
DIR=$(cd "$(dirname "$0")" && pwd)
for s in "$@"; do
  echo "=== $(date -u +%FT%TZ) reset for $s ($LABEL)"
  if ! bash "$DIR/reset-stack.sh" > /tmp/payflow-reset.log 2>&1; then
    echo "reset failed; skipping $s (results would not be comparable)"; tail -3 /tmp/payflow-reset.log; continue
  fi
  tail -1 /tmp/payflow-reset.log
  SKIP_PRE_QUIESCE=true DURATION=90s bash "$DIR/run.sh" smoke "warmup-$LABEL" > /dev/null 2>&1
  echo "=== $(date -u +%FT%TZ) $s ($LABEL)"
  SKIP_PRE_QUIESCE=true QUIESCE_TIMEOUT=${QUIESCE_TIMEOUT:-300} bash "$DIR/run.sh" "$s" "$LABEL" 2>&1 \
    | grep -E "^evidence|did not quiesce|^\| (accepted_per_s|api_post_p99_ms|saga_p99_ms|outbox_publish_delay_p99_ms|dropped_iterations|drain_seconds_after_load|saga_open_after_drain) "
done
echo "=== $(date -u +%FT%TZ) campaign $LABEL done"
