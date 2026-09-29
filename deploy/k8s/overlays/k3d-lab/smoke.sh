#!/usr/bin/env bash
# End-to-end smoke test of the k3d deployment: one funded payer, one payee, one payment through the pod to SETTLED.
# Tokens come from the compose Keycloak (host port); the API is reached with a port-forward to the pod's Service.
#   ENV_FILE=.env LAB_KUBECONFIG=... deploy/k8s/overlays/k3d-lab/smoke.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
: "${ENV_FILE:=$ROOT/.env}"
set -a; . "$ENV_FILE"; set +a
: "${LAB_KUBECONFIG:=${TMPDIR:-/tmp}/payflow-lab.kubeconfig}"
K="kubectl --kubeconfig $LAB_KUBECONFIG --context k3d-payflow-lab -n payflow"
PORT=${SMOKE_PORT:-18080}
TOKEN_URL="http://localhost:${PAYFLOW_KEYCLOAK_HOST_PORT:-8081}/realms/payflow/protocol/openid-connect/token"
API="http://localhost:$PORT/api/v1"

$K port-forward svc/payflow "$PORT:8080" >/dev/null 2>&1 & PF=$!
trap 'kill $PF 2>/dev/null' EXIT
for _ in $(seq 30); do curl -sf "http://localhost:$PORT/actuator/health/readiness" >/dev/null && break; sleep 1; done

field() { python -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }
USER_TOKEN=$(curl -sf "$TOKEN_URL" -d grant_type=password -d client_id=payflow-customer-app -d username=perf-001 \
  --data-urlencode "password=$PAYFLOW_PERF_USER_PASSWORD" | field access_token)
TREASURY=$(curl -sf "$TOKEN_URL" -d grant_type=client_credentials -d client_id=payflow-treasury \
  --data-urlencode "client_secret=$PAYFLOW_TREASURY_CLIENT_SECRET" | field access_token)
auth() { echo "Authorization: Bearer $1"; }
uuid() { python -c "import uuid; print(uuid.uuid4())"; }
open_account() { curl -sf "$API/accounts" -H "$(auth "$USER_TOKEN")" -H 'Content-Type: application/json' \
  -d "{\"displayName\":\"k3d smoke $1\",\"currency\":\"USD\"}" | field id; }

PAYER=$(open_account payer); PAYEE=$(open_account payee)
curl -sf "$API/accounts/$PAYER/deposits" -H "$(auth "$TREASURY")" -H 'Content-Type: application/json' \
  -d "{\"depositId\":\"$(uuid)\",\"amount\":\"100.00\",\"currency\":\"USD\"}" >/dev/null
PAYMENT=$(curl -sf "$API/payments" -H "$(auth "$USER_TOKEN")" -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuid)" \
  -d "{\"payerAccountId\":\"$PAYER\",\"payeeAccountId\":\"$PAYEE\",\"amount\":\"25.00\",\"currency\":\"USD\",\"method\":\"CARD\",\"reference\":\"k3d-smoke\",\"checkout\":{\"deviceId\":\"k3d\",\"ipAddress\":\"203.0.113.10\",\"countryCode\":\"US\"}}" | field id)
for _ in $(seq 60); do
  STATUS=$(curl -sf "$API/payments/$PAYMENT" -H "$(auth "$USER_TOKEN")" | field status)
  [ "$STATUS" = SETTLED ] || [ "$STATUS" = FAILED ] && break; sleep 1
done
echo "payment=$PAYMENT status=$STATUS"
[ "$STATUS" = SETTLED ]
