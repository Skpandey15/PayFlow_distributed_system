#!/bin/sh
# Least-privilege database roles (runs once, on first container start):
#   payflow_migrator - owns the schemas; used only by Flyway (DDL)
#   payflow_app      - runtime role; DML only, granted per table by R__runtime_role_grants.sql
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v migrator_pw="$PAYFLOW_DB_MIGRATION_PASSWORD" -v app_pw="$PAYFLOW_DB_PASSWORD" <<'EOSQL'
CREATE ROLE payflow_migrator LOGIN PASSWORD :'migrator_pw';
CREATE ROLE payflow_app LOGIN PASSWORD :'app_pw';

ALTER DATABASE payflow OWNER TO payflow_migrator;
ALTER SCHEMA public OWNER TO payflow_migrator;

REVOKE ALL ON DATABASE payflow FROM PUBLIC;
GRANT CONNECT ON DATABASE payflow TO payflow_migrator, payflow_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOSQL
