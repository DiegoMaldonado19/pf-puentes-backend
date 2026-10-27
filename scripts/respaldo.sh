#!/bin/sh
# DT-DEP-07 y DT-ALM-05: respaldo diario de PostgreSQL y de los objetos de MinIO, con la misma prioridad.
# Retención: 30 diarios y 12 mensuales (los del día 1). Lo corre el cron del host
# (docs/guia-respaldo-y-restauracion.md). Uso: scripts/respaldo.sh <dev|stage|prod>
set -eu

ENTORNO=${1:?uso: respaldo.sh <dev|stage|prod>}
RAIZ=${SGP_RAIZ:-$HOME/puentes}
DESTINO="$RAIZ/respaldos/$ENTORNO"
FECHA=$(date +%Y%m%d)

# Un corte a mitad nunca deja un respaldo que parezca completo.
trap 'rm -f "$DESTINO"/diario/*.parcial' EXIT

# El compose del entorno lee su .env: nombre del proyecto, red y credenciales.
cd "$RAIZ/$ENTORNO/pf-puentes-backend"
mkdir -p "$DESTINO/diario" "$DESTINO/mensual" "$DESTINO/espejo/archivos"

# -Fc: comprimido y apto para pg_restore.
docker compose exec -T postgres pg_dump -U puentes_migrador -d puentes -Fc \
  >"$DESTINO/diario/bd-$FECHA.dump.parcial"
mv "$DESTINO/diario/bd-$FECHA.dump.parcial" "$DESTINO/diario/bd-$FECHA.dump"

# Contenedor de un solo uso con la imagen y las credenciales del servicio minio, que trae mc.
docker compose run --rm --no-deps -v "$DESTINO/espejo:/espejo" --entrypoint sh minio -c \
  'mc alias set origen http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null &&
   mc mirror --overwrite --remove --quiet origen/archivos /espejo/archivos'
cd "$DESTINO"
tar -czf "diario/minio-$FECHA.tar.gz.parcial" -C espejo .
mv "diario/minio-$FECHA.tar.gz.parcial" "diario/minio-$FECHA.tar.gz"

if [ "$(date +%d)" = 01 ]; then
  cp "diario/bd-$FECHA.dump" "diario/minio-$FECHA.tar.gz" mensual/
fi

# Los nombres llevan la fecha: en orden inverso, lo que pasa de N es lo más viejo.
podar() {
  find "$1" -maxdepth 1 -name "$2-[0-9]*" | sort -r | tail -n +"$(($3 + 1))" | xargs -r rm -f
}
podar diario bd 30
podar diario minio 30
podar mensual bd 12
podar mensual minio 12

echo "$(date '+%Y-%m-%d %H:%M:%S') respaldo $ENTORNO: bd-$FECHA.dump y minio-$FECHA.tar.gz"
