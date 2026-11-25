#!/usr/bin/env bash
# Runs once, on the first start of an empty PostgreSQL volume.
# Database per service: each service gets its own database and login role,
# and no role can connect to another service's database.
set -euo pipefail

create_service_database() {
    local name="$1" password="$2"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE ${name} LOGIN PASSWORD '${password}';
CREATE DATABASE ${name} OWNER ${name};
REVOKE ALL ON DATABASE ${name} FROM PUBLIC;
SQL
}

create_service_database ingestion "${INGESTION_DB_PASSWORD}"
create_service_database analysis "${ANALYSIS_DB_PASSWORD}"
