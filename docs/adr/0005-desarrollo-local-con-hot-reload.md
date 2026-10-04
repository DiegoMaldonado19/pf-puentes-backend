# 0005. Desarrollo local con hot reload

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-DEP-06

## Contexto
El entorno de desarrollo local tiene que levantar postgres, minio y el backend sin instalar nada aparte de Docker, y recargar al guardar. Dev, stage y prod no llevan hot reload.

## Decisión
- `compose.local.yaml` reutiliza con `extends` los servicios de `compose.yaml`.
- El backend se construye desde la etapa `dev` del `Dockerfile` y corre `mvn spring-boot:run` con `spring-boot-devtools`.
- Con `docker compose -f compose.local.yaml up --watch`:
  - al cambiar algo en `src/`, Compose lo sincroniza y ejecuta `mvn -q compile`, y devtools reinicia la app en segundos;
  - al cambiar `pom.xml`, se reconstruye la imagen.

## Consecuencias
- Se usan las mismas imágenes de postgres y minio que en la nube.
- Requiere Docker Compose 2.32 o superior, por la acción `sync+exec`.
- devtools es una dependencia opcional y no entra al jar de producción.
