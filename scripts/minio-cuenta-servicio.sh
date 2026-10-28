#!/bin/sh
# DT-DEP-11: crea o renueva la cuenta de servicio del backend en MinIO, con permisos solo sobre el bucket archivos.
# Lee MINIO_APP_USER y MINIO_APP_PASSWORD del .env del entorno. Es idempotente.
# Uso: scripts/minio-cuenta-servicio.sh <dev|stage|prod>
set -eu

ENTORNO=${1:?uso: minio-cuenta-servicio.sh <dev|stage|prod>}
RAIZ=${SGP_RAIZ:-$HOME/puentes}
cd "$RAIZ/$ENTORNO/pf-puentes-backend"
set -a
. ./.env
set +a
: "${MINIO_APP_USER:?MINIO_APP_USER vacío en .env}"
: "${MINIO_APP_PASSWORD:?MINIO_APP_PASSWORD vacío en .env}"
if [ "$MINIO_APP_USER" = "$MINIO_ROOT_USER" ]; then
  echo "MINIO_APP_USER debe ser distinto de MINIO_ROOT_USER" >&2
  exit 1
fi

docker compose exec -T -e APP_USUARIO="$MINIO_APP_USER" -e APP_CLAVE="$MINIO_APP_PASSWORD" minio sh -c '
  set -eu
  mc alias set local http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
  mc mb --ignore-existing local/archivos >/dev/null
  printf "%s" "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:ListBucket\"],\"Resource\":[\"arn:aws:s3:::archivos\"]},{\"Effect\":\"Allow\",\"Action\":[\"s3:GetObject\",\"s3:PutObject\",\"s3:DeleteObject\"],\"Resource\":[\"arn:aws:s3:::archivos/*\"]}]}" > /tmp/sgp-archivos.json
  mc admin policy create local sgp-archivos /tmp/sgp-archivos.json >/dev/null
  mc admin user add local "$APP_USUARIO" "$APP_CLAVE" >/dev/null
  mc admin policy attach local sgp-archivos --user "$APP_USUARIO" >/dev/null
'
echo "$(date '+%Y-%m-%d %H:%M:%S') cuenta de servicio $MINIO_APP_USER lista en $ENTORNO"
