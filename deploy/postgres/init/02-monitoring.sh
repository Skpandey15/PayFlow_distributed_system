#!/bin/sh
# Monitoring role for postgres-exporter: pg_monitor only (read statistics views), no access to business tables.
# pg_stat_statements provides per-statement latency for bottleneck analysis (loaded via shared_preload_libraries).
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v monitor_pw="$PAYFLOW_DB_MONITOR_PASSWORD" <<'EOSQL'
CREATE ROLE payflow_monitor LOGIN PASSWORD :'monitor_pw';
GRANT pg_monitor TO payflow_monitor;
GRANT CONNECT ON DATABASE payflow TO payflow_monitor;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
EOSQL
