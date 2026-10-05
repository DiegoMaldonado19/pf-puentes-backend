# 0013. URL firmadas de MinIO servidas por nginx

- Estado: aceptado
- Fecha: 04/10/2026
- Directriz o regla relacionada: DT-SEC-09, DT-ALM-01, DT-SEC-12, DT-DEP-02

## Contexto
DT-SEC-09 pide servir los archivos con URL firmadas de vigencia limitada o por un endpoint autorizado, sin exponer el almacenamiento. MinIO no publica puertos: el navegador no alcanza `minio:9000`. Además, el token de acceso vive en memoria (DT-SEC-02), así que un `<img src>` no puede mandarlo a un endpoint autorizado.

## Decisión
- `AlmacenamientoMinio.obtenerUrlFirmada` firma un GET de 15 minutos con el **origen público** del entorno (`MINIO_URL_PUBLICA`, p. ej. `https://pf-puentes.duckdns.org:8081`), en estilo path: `/<bucket>/<clave>?X-Amz-...`.
- El nginx del frontend reenvía `/archivos/` a `minio:9000`:
  - solo GET;
  - con el mismo `Host`, porque la firma lo incluye;
  - sin access log, porque la query lleva la firma (DT-SEC-12).
- El bucket se llama `archivos` y no `puentes`, porque `/puentes/...` es una ruta de la SPA.
- Quien lista archivos (`ArchivoService.listar`) recibe las URL ya firmadas. Lo purgado no se firma (RN-ARC-06).

## Consecuencias
- `<img src>` funciona sin token, y el backend no transmite los bytes de las fotos.
- MinIO sigue sin puertos publicados: solo se alcanza un objeto con una firma vigente.
- Una URL filtrada sirve hasta 15 minutos.
- Cada entorno necesita `MINIO_URL_PUBLICA`; en local es `http://localhost:9000`, donde MinIO sí publica su puerto.
- Si algún día se usa `AlmacenamientoSistemaArchivos` (DT-ALM-02), habrá que servir los archivos por un endpoint autorizado.
