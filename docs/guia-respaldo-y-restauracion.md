# Guía de respaldo y restauración

Cumple DT-DEP-07 (respaldo diario con retención de 30 diarios y 12 mensuales), DT-DEP-08 (prueba de restauración documentada) y DT-ALM-05 (los archivos con la misma prioridad que la base). Metas: **RPO de 24 h** y **RTO de 4 h** (RNF-10 y RNF-11).

## Qué se respalda

| Qué | Cómo | Archivo |
|---|---|---|
| Base de datos `puentes` | `pg_dump -Fc` dentro del contenedor `postgres` | `bd-AAAAMMDD.dump` |
| Objetos de MinIO: bucket `archivos` (fotos, miniaturas, PDF y evidencias) | `mc mirror` a un espejo y `tar.gz` del espejo | `minio-AAAAMMDD.tar.gz` |

- Los dos se respaldan juntos, con la misma retención.
- El certificado TLS no se respalda: `scripts/certificado-tls.sh` del frontend emite otro.

## Dónde y cuánto tiempo

```
~/puentes/respaldos/<entorno>/
├── diario/    30 días: bd-AAAAMMDD.dump y minio-AAAAMMDD.tar.gz
├── mensual/   12 meses: copia de los del día 1
└── espejo/    espejo del bucket; se actualiza cada noche
```

- Está fuera de los volúmenes de Docker: si se borra un volumen, el respaldo sigue ahí.
- Un respaldo a medias nunca queda con su nombre final: se escribe como `.parcial` y se renombra al terminar.
- **Riesgo:** los respaldos viven en la misma EC2; si se pierde la instancia, se pierden con ella. Por eso conviene la [copia fuera de la EC2](#copia-fuera-de-la-ec2).

## Instalación (una vez, en la EC2)

1. Revisar la zona horaria y el NTP del host (DT-DEP-12):

   ```sh
   timedatectl
   ```

2. Crear la carpeta del log y agregar la tarea con `crontab -e`. Corre a las 02:30 de Guatemala, antes de la purga de archivos (03:00) y del aviso de borradores (04:00):

   ```sh
   mkdir -p ~/puentes/respaldos
   # Host en UTC (02:30 en Guatemala = 08:30 UTC):
   30 8 * * * $HOME/puentes/prod/pf-puentes-backend/scripts/respaldo.sh prod >> $HOME/puentes/respaldos/respaldo.log 2>&1
   ```

   Si el host está en `America/Guatemala`, la hora es `30 2 * * *`.

3. Correrlo una vez a mano y revisar que aparezcan los dos archivos del día:

   ```sh
   ~/puentes/prod/pf-puentes-backend/scripts/respaldo.sh prod
   ls -l ~/puentes/respaldos/prod/diario
   ```

El script llega con cada despliegue a la carpeta del entorno (el CI hace `git checkout` del repo). Para respaldar stage, se agrega otra línea con `stage` en otro horario.

## Prueba de restauración (DT-DEP-08)

```sh
~/puentes/prod/pf-puentes-backend/scripts/restaurar.sh prod AAAAMMDD [--conservar]
```

Qué hace:
1. Busca el respaldo de esa fecha en `diario/` y, si no está, en `mensual/`.
2. Levanta un postgres y un minio **limpios** en otro proyecto (`puentes-restauracion-backend`, red `puentes-restauracion`). El entorno en uso no se toca.
3. Restaura la base con `pg_restore` y los objetos con `mc mirror`.
4. Verifica dos cosas:
   - que en MinIO exista cada objeto que la tabla `archivo` dice tener (lo purgado por RN-ARC-06 no cuenta);
   - que se puedan abrir todos los archivos de la inspección con más fotos.
5. Imprime cuánto tardó y borra lo que levantó, aunque algo falle a mitad. Con `--conservar` lo deja corriendo para revisarlo.

Si falta algo, termina con código 1 y dice qué falta.

### Registro de pruebas

| Fecha | Dónde | Respaldo | Duración | Objetos (base / MinIO) | Resultado | Quién |
|---|---|---|---|---|---|---|
| 27/10/2026 | Ensayo local del script (Docker Desktop, datos de prueba) | del mismo día | 11 s | 11 / 11; con un objeto borrado a propósito, 11 / 10 | ✅ Restauró todo; el faltante provocado lo detectó (código 1) y limpió igual | P1 |
| _pendiente_ | prod (EC2) | | | | | P1 |

## Restauración real

Cuando se perdió el entorno o su base:

1. Si se perdió la EC2: preparar el host (Docker, `~/puentes` y el certificado) y desplegar con el CI, que deja el entorno corriendo con una base nueva.
2. Copiar el respaldo a `~/puentes/respaldos/<entorno>/diario/`, o usar el que ya está.
3. Detener el backend y recrear la base vacía. Es el mismo camino que ensaya `restaurar.sh`: base vacía + `pg_restore`.

   ```sh
   cd ~/puentes/<entorno>/pf-puentes-backend
   docker compose stop backend
   docker compose exec -T postgres dropdb -U puentes_migrador --force puentes
   docker compose exec -T postgres createdb -U puentes_migrador puentes
   docker compose exec -T postgres pg_restore -U puentes_migrador -d puentes --exit-on-error \
     < ~/puentes/respaldos/<entorno>/diario/bd-AAAAMMDD.dump
   ```

4. Restaurar los objetos:

   ```sh
   mkdir -p /tmp/restaurar && tar -xzf ~/puentes/respaldos/<entorno>/diario/minio-AAAAMMDD.tar.gz -C /tmp/restaurar
   docker compose run --rm --no-deps -v /tmp/restaurar:/espejo:ro --entrypoint sh minio -c \
     'mc alias set destino http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" &&
      mc mb --ignore-existing destino/archivos && mc mirror --overwrite /espejo/archivos destino/archivos'
   ```

5. Arrancar el backend con `docker compose start backend`. Flyway encuentra las migraciones ya aplicadas en el respaldo.
6. Revisar: entrar como Administrador, abrir una inspección con fotos y consultar la bitácora.

## Copia fuera de la EC2

Desde una computadora con el acceso SSH del despliegue (alias `ec2`), al menos una vez por semana:

```sh
scp 'ec2:~/puentes/respaldos/prod/diario/*-AAAAMMDD.*' ./respaldos-sgp/
```

## Problemas comunes

| Síntoma | Qué revisar |
|---|---|
| `No hay respaldo del AAAAMMDD` | La fecha, y `ls ~/puentes/respaldos/<entorno>/diario` |
| `ERROR: faltan objetos en MinIO` | El respaldo de MinIO de esa noche no tiene todo lo que la base dice: revisar `respaldo.log` |
| El cron no corre | `grep CRON /var/log/syslog`, y que los scripts sean ejecutables (`ls -l scripts/`) |
| `pg_restore` falla en la restauración real | Que la base esté vacía (paso 3) y que el backend esté detenido |
