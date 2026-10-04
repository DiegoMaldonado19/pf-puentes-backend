#!/bin/sh
# DT-BD-13: la aplicación solo tiene DML. Corre una vez, al crear el volumen.
set -e
psql -v ON_ERROR_STOP=1 -v clave="$DB_APP_PASSWORD" -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
CREATE ROLE puentes_app LOGIN PASSWORD :'clave';
GRANT USAGE ON SCHEMA public TO puentes_app;
ALTER DEFAULT PRIVILEGES FOR ROLE puentes_migrador IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO puentes_app;
ALTER DEFAULT PRIVILEGES FOR ROLE puentes_migrador IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO puentes_app;
SQL
