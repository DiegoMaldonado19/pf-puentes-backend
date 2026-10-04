# 0008. Imágenes base con versión fija

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-ALM-01, DT-DEP-01

## Contexto
`latest` y los tags móviles hacen que dos builds del mismo código den imágenes distintas. Además, el proyecto MinIO dejó de publicar imágenes: `minio/minio` ya no existe en Docker Hub y quay exige autenticación.

## Decisión
- Toda imagen y toda GitHub Action lleva una versión exacta. Las versiones están en el README.
- MinIO viene de `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`, un fork comunitario que sigue compilando MinIO con la misma API S3 y el mismo entrypoint.
- postgis no publica tags de parche, así que se usa `postgis/postgis:17-3.6-alpine` (PostgreSQL 17 con PostGIS 3.6).

## Consecuencias
- Los builds son reproducibles, y actualizar una versión es un cambio explícito en un PR.
- Se sigue cumpliendo DT-ALM-01, porque sigue siendo MinIO.
- Las actualizaciones hay que revisarlas a mano.
- Para MinIO dependemos de un tercero. Si deja de publicar, la alternativa es otro almacenamiento compatible con S3 detrás de `AlmacenamientoArchivos` (DT-ALM-03).
