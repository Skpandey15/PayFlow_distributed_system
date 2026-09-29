#!/usr/bin/env bash
# Returns the compose lab to an empty, identical starting state between load tests: data volumes (PostgreSQL, Kafka,
# MongoDB) are wiped, Prometheus history is kept (earlier runs stay queryable), perf users are re-provisioned.
# Used when a run leaves more backlog than is worth draining, and to give every compared scenario a fresh database.
#
# Every Docker step is retried: on the Windows lab host the Rancher Desktop socket intermittently refused connections
# after hours of heavy use ("lacked sufficient buffer space"); a reset must never leave a half-created stack.
#   ENV_FILE=.env performance/scripts/reset-stack.sh
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${ENV_FILE:=$ROOT/.env}"
cd "$ROOT"
compose() { docker compose --env-file "$ENV_FILE" "$@"; }
retry() { # retry <description> <command...>: up to 12 attempts, 15 s apart
  local what=$1; shift
  for attempt in $(seq 1 12); do
    "$@" >/dev/null 2>&1 && return 0
    echo "  $what failed (attempt $attempt), retrying" >&2; sleep 15
  done
  echo "  $what failed permanently" >&2; return 1
}
wait_docker() { for _ in $(seq 1 40); do docker version >/dev/null 2>&1 && return 0; sleep 15; done; return 1; }

wait_docker || { echo "docker daemon unreachable" >&2; exit 1; }
retry "stop/remove app and data services" compose rm -sf payflow kafka postgres mongo keycloak postgres-exporter settlement-rail
retry "remove data volumes" docker volume rm -f payflow_pgdata payflow_kafkadata payflow_mongodata
retry "start stack" compose up -d
for _ in $(seq 1 90); do
  s=$(docker inspect -f '{{.State.Health.Status}}' payflow-payflow-1 2>/dev/null || true)
  c=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PAYFLOW_KEYCLOAK_HOST_PORT:-8081}/realms/payflow/.well-known/openid-configuration" || true)
  [ "$s" = healthy ] && [ "$c" = 200 ] && break
  # A container may have been left un-started by an interrupted daemon call: keep asking compose to converge.
  [ "$s" = "" ] && compose up -d >/dev/null 2>&1
  sleep 5
done
expected=$(compose config --services | wc -l)
running=$(compose ps --status running --services 2>/dev/null | wc -l)
[ "$running" -ge "$expected" ] || { echo "stack incomplete: $running/$expected services running" >&2; exit 1; }
ENV_FILE="$ENV_FILE" bash "$ROOT/performance/scripts/provision-perf-users.sh" 50 || exit 1
# Prometheus caches its scrape token; the new Keycloak has new signing keys, so force a fresh token (data kept).
retry "restart prometheus" docker restart payflow-prometheus-1
echo "stack reset: $(date -u +%FT%TZ)"
