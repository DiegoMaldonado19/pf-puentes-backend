#!/bin/sh
# DT-DEP-08: restaura un respaldo en un proyecto Compose aparte y mide cuánto tarda (RTO de 4 h, RNF-11).
# No toca el entorno en uso: levanta postgres y minio limpios en puentes-restauracion-backend.
# Uso: scripts/restaurar.sh <dev|stage|prod> <AAAAMMDD> [--conservar]
set -eu

USO='uso: restaurar.sh <dev|stage|prod> <AAAAMMDD> [--conservar]'
ENTORNO=${1:?$USO}
FECHA=${2:?$USO}
CONSERVAR=${3:-}
RAIZ=${SGP_RAIZ:-$HOME/puentes}
ORIGEN="$RAIZ/respaldos/$ENTORNO"
TRABAJO="$RAIZ/respaldos/restauracion"
inicio=$(date +%s)

RESPALDO=
for carpeta in diario mensual; do
  if [ -f "$ORIGEN/$carpeta/bd-$FECHA.dump" ]; then
    RESPALDO="$ORIGEN/$carpeta"
    break
  fi
done
if [ -z "$RESPALDO" ]; then
  echo "No hay respaldo del $FECHA en $ORIGEN" >&2
  exit 1
fi

# Mismo compose y mismos secretos que el entorno; otro proyecto y otra red, con volúmenes vacíos.
cd "$RAIZ/$ENTORNO/pf-puentes-backend"
export APP_ENV=restauracion
compose() { docker compose -p puentes-restauracion-backend "$@"; }
mc_destino() {
  compose run --rm --no-deps "$@" --entrypoint sh minio -c \
    "mc alias set destino http://minio:9000 \"\$MINIO_ROOT_USER\" \"\$MINIO_ROOT_PASSWORD\" >/dev/null && $MC"
}
consulta() { compose exec -T postgres psql -U puentes_migrador -d puentes -tAc "$1" | tr -d '[:space:]'; }
# También si algo falla a mitad: el proyecto de restauración no queda corriendo.
# shellcheck disable=SC2317 # la invoca el trap
limpiar() {
  if [ "$CONSERVAR" != --conservar ]; then
    compose down -v --remove-orphans >/dev/null 2>&1 || true
    docker network rm puentes-restauracion >/dev/null 2>&1 || true
    rm -rf "$TRABAJO"
  fi
}
trap limpiar EXIT

docker network create puentes-restauracion >/dev/null 2>&1 || true
compose up -d --wait postgres minio
compose exec -T postgres pg_restore -U puentes_migrador -d puentes --exit-on-error \
  <"$RESPALDO/bd-$FECHA.dump"

rm -rf "$TRABAJO"
mkdir -p "$TRABAJO"
tar -xzf - -C "$TRABAJO" <"$RESPALDO/minio-$FECHA.tar.gz"
MC='mc mb --ignore-existing --quiet destino/archivos && mc mirror --overwrite --quiet /espejo/archivos destino/archivos'
mc_destino -v "$TRABAJO:/espejo:ro"

# Cada objeto que la base dice tener debe existir; lo purgado no cuenta (RN-ARC-06).
esperados=$(consulta "SELECT count(clave) + count(clave_miniatura) FROM archivo WHERE purgado_en IS NULL")
MC='mc ls --recursive destino/archivos | wc -l'
objetos=$(mc_destino | tr -d '[:space:]')
inspeccion=$(consulta "SELECT inspeccion_id FROM archivo WHERE inspeccion_id IS NOT NULL AND purgado_en IS NULL
  GROUP BY inspeccion_id ORDER BY count(*) DESC LIMIT 1")
resultado=0
verificados=0
if [ -n "$inspeccion" ]; then
  claves=$(compose exec -T postgres psql -U puentes_migrador -d puentes -tAc \
    "SELECT clave FROM archivo WHERE inspeccion_id = '$inspeccion' AND purgado_en IS NULL
     UNION ALL SELECT clave_miniatura FROM archivo
     WHERE inspeccion_id = '$inspeccion' AND purgado_en IS NULL AND clave_miniatura IS NOT NULL")
  # shellcheck disable=SC2016 # $CLAVES y $c se expanden dentro del contenedor, a propósito
  MC='for c in $CLAVES; do mc stat --quiet "destino/archivos/$c" >/dev/null || exit 1; done'
  if mc_destino -e CLAVES="$claves"; then
    verificados=$(printf '%s\n' "$claves" | grep -c .)
  else
    echo "ERROR: faltan objetos de la inspección $inspeccion" >&2
    resultado=1
  fi
fi
if [ "$objetos" -lt "$esperados" ]; then
  echo "ERROR: faltan objetos en MinIO" >&2
  resultado=1
fi

duracion=$(($(date +%s) - inicio))
echo "Restauración de $ENTORNO del $FECHA: $((duracion / 60)) min $((duracion % 60)) s (meta: 4 h)"
echo "Objetos según la base: $esperados · en MinIO: $objetos · inspección ${inspeccion:-ninguna}: $verificados archivos verificados"
exit "$resultado"
