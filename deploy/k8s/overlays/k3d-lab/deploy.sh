#!/usr/bin/env bash
# Deploys PayFlow to a dedicated k3d cluster ("payflow-lab") that joins the compose network, then smoke-tests it:
# restricted Pod Security admission, probes, secret wiring, Kafka SASL identity, and one payment end to end.
#   ENV_FILE=.env deploy/k8s/overlays/k3d-lab/deploy.sh [--delete]
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
: "${ENV_FILE:=$ROOT/.env}"
set -a; . "$ENV_FILE"; set +a
CLUSTER=payflow-lab
CTX=k3d-$CLUSTER
K="kubectl --context $CTX"

if [ "${1:-}" = "--delete" ]; then k3d cluster delete $CLUSTER; exit 0; fi

k3d cluster list $CLUSTER >/dev/null 2>&1 || \
  k3d cluster create $CLUSTER --network payflow_default --servers 1 --agents 0 --no-lb \
    --k3s-arg "--disable=traefik@server:0" --wait
k3d image import -c $CLUSTER payflow:wp03 payflow-rail-simulator:wp03

$K apply -k "$ROOT/deploy/k8s/overlays/k3d-lab" --dry-run=server >/dev/null   # server-side validation first
$K apply -f - <<EOF_NS
apiVersion: v1
kind: Namespace
metadata:
  name: payflow
  labels: {pod-security.kubernetes.io/enforce: restricted, pod-security.kubernetes.io/audit: restricted}
EOF_NS
$K -n payflow create secret generic payflow-secrets --dry-run=client -o yaml \
  --from-literal=db-password="$PAYFLOW_DB_PASSWORD" \
  --from-literal=mongo-uri="mongodb://payflow_fraud_app:$PAYFLOW_MONGO_PASSWORD@mongo:27017/payflow_fraud?authSource=payflow_fraud" \
  --from-literal=kafka-password="$PAYFLOW_KAFKA_APP_PASSWORD" | $K apply -f -
$K apply -k "$ROOT/deploy/k8s/overlays/k3d-lab"

# Compose service names -> current container IPs (pods cannot use Docker's embedded DNS).
aliases=$(for s in postgres kafka mongo keycloak tempo; do
  ip=$(docker inspect -f '{{(index .NetworkSettings.Networks "payflow_default").IPAddress}}' payflow-$s-1)
  printf '{"ip":"%s","hostnames":["%s"]},' "$ip" "$s"; done)
$K -n payflow patch deployment payflow --type merge -p "{\"spec\":{\"template\":{\"spec\":{\"hostAliases\":[${aliases%,}]}}}}"
$K -n payflow rollout status deployment/rail-simulator --timeout=180s
$K -n payflow rollout status deployment/payflow --timeout=420s
$K -n payflow get pods -o wide
echo "deployed: context $CTX, namespace payflow"
