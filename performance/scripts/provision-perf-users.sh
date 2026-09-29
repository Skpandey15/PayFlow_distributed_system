#!/usr/bin/env bash
# Creates the synthetic load-test customers perf-001..perf-NNN in the LOCAL lab realm via the Keycloak admin API.
# They are deliberately not in deploy/keycloak/realm-payflow.json: synthetic users never belong in a realm export
# that could be promoted. Idempotent (409 = already exists).
#   ENV_FILE=path/to/.env performance/scripts/provision-perf-users.sh [count]
set -euo pipefail
COUNT=${1:-50}
: "${ENV_FILE:=.env}"
set -a; . "$ENV_FILE"; set +a
KC=${PAYFLOW_KEYCLOAK_URL:-http://localhost:${PAYFLOW_KEYCLOAK_HOST_PORT:-8081}}
ADMIN=$(curl -sf "$KC/realms/master/protocol/openid-connect/token" -d grant_type=password -d client_id=admin-cli \
  -d username=admin --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" | python -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
created=0
for i in $(seq 1 "$COUNT"); do
  u=$(printf 'perf-%03d' "$i")
  body=$(python - "$u" <<'PY'
import json, os, sys
u = sys.argv[1]
print(json.dumps({"username": u, "enabled": True, "email": f"{u}@perf.payflow.example", "emailVerified": True,
                  "firstName": "Perf", "lastName": u, "credentials": [{"type": "password",
                  "value": os.environ["PAYFLOW_PERF_USER_PASSWORD"], "temporary": False}]}))
PY
)
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$KC/admin/realms/payflow/users" \
    -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d "$body")
  case $code in 201) created=$((created+1));; 409) ;; *) echo "user $u: HTTP $code" >&2; exit 1;; esac
done
echo "perf users ready: $COUNT (newly created: $created)"
