# Guía de despliegue

Cumple DT-CAL-08 y DT-DEP-01 a DT-DEP-12. Complementa la [guía de respaldo y restauración](guia-respaldo-y-restauracion.md) y las decisiones de [docs/adr](adr/README.md).

## Qué se despliega

Cuatro servicios en Docker Compose, en una EC2 de AWS (ADR 0001 del backend):

| Servicio | Repositorio | Puertos publicados |
|---|---|---|
| `frontend` (nginx: SPA, proxy `/api` y `/archivos`) | pf-puentes-frontend | Solo este, en `HTTPS_PORT` |
| `backend` (Spring Boot) | pf-puentes-backend | Ninguno |
| `postgres` (PostGIS 17) | pf-puentes-backend | Ninguno |
| `minio` (almacenamiento S3) | pf-puentes-backend | Ninguno |

Hay tres entornos en la misma EC2, cada uno con su proyecto Compose y su red:

| Evento en GitHub | Entorno | Carpeta en la EC2 | Puerto HTTPS |
|---|---|---|---|
| PR hacia `develop` | dev | `~/puentes/dev/` | 8082 |
| Push a `develop` | stage | `~/puentes/stage/` | 8081 |
| Push a `main` | prod | `~/puentes/prod/` | 443 (y 80 solo redirige) |

## Flujo de promoción

1. Una rama se integra a `develop` por PR, con el CI en verde.
2. El push a `develop` despliega stage. La imagen de ese árbol se construye una sola vez.
3. Prod solo recibe imágenes que pasaron por stage (`ci-cd.yml`, job `push`). Si un árbol no pasó por stage, el despliegue a prod falla a propósito.
4. Nunca se mueve `develop` a la fuerza ni se hace push directo a `main`: a `main` solo entra el PR de `develop`.

## Secretos por entorno (GitHub Environments)

El job `deploy` escribe el `.env` de cada entorno desde su Environment (ADR 0004 del backend y ADR 0005 del frontend). Hay que definirlos en **dev, stage y prod**:

- Backend: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `DB_MIGRATION_PASSWORD`, `DB_APP_PASSWORD`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_APP_USER`, `MINIO_APP_PASSWORD`, `MINIO_URL_PUBLICA`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `ADMIN_INICIAL_*`, `FRONTEND_URL`, `COOKIE_SECURE`, `CORREO_MODO`, `CORREO_REMITENTE`, `SMTP_*`.
- Frontend: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `HTTPS_PORT` (dev 8082, stage 8081, prod 443). Si `HTTPS_PORT` falta, el despliegue se detiene.

`MINIO_APP_USER` y `MINIO_APP_PASSWORD` son la cuenta de servicio del backend; deben configurarse juntos y el usuario debe ser distinto del root. El deploy inicia primero PostgreSQL y MinIO, crea o actualiza la cuenta y su política, y solo después arranca el backend. Si faltan ambos, se conserva la compatibilidad con la cuenta root; si falta uno, el bootstrap falla antes de iniciar el backend.

## Primer arranque de un entorno

1. Preparar el host: Docker, la carpeta `~/puentes` y el certificado TLS (`scripts/certificado-tls.sh` del frontend, README del frontend). Los tres entornos comparten el certificado de `pf-puentes.duckdns.org`.
2. Crear la red del entorno: `docker network create puentes-<entorno>` (el job de deploy también lo hace si falta).
3. Configurar los secretos del Environment, incluidos ambos `MINIO_APP_*`.
4. Desplegar con el CI: espera PostgreSQL y MinIO, ejecuta el bootstrap de la cuenta y después espera el backend, que aplica las migraciones Flyway. No hace falta un primer deploy con root ni crear la cuenta después.
5. Crear el juego de pesos provisional de la versión activa del formulario desde **/admin/pesos**. Sin él, publicar una inspección responde `ic-no-calculable`. Cada entorno necesita el suyo; no hay siembra automática (ADR 0040 del backend).
6. Revisar que el healthcheck está sano: `cd ~/puentes/<entorno>/pf-puentes-backend && docker compose ps`.

## Cuenta de servicio de MinIO (DT-DEP-11)

El contenedor de MinIO corre como uid 1000. Al primer arranque, el entrypoint de `compose.yaml` cambia el dueño de `/data` si hace falta, así que los volúmenes nuevos y los heredados funcionan sin pasos manuales.

Para crear la cuenta de servicio, con `MINIO_APP_USER` y `MINIO_APP_PASSWORD` en el `.env` del entorno:

```sh
~/puentes/<entorno>/pf-puentes-backend/scripts/minio-cuenta-servicio.sh <entorno>
```

El pipeline lo ejecuta antes del backend. El comando anterior permite repetirlo manualmente con MinIO ya sano. El script es ejecutable, idempotente y detiene el proceso ante cualquier error; actualizar la cuenta no la elimina primero. La cuenta solo puede leer, escribir y borrar objetos del bucket `archivos`, y listar ese bucket (que es lo que valida el backend al arrancar). No puede crear buckets ni administrar usuarios.

## Logs (DT-DEP-09)

- Las bitácoras del backend son JSON (formato ECS) y se escriben en el volumen `logs` en `/app/logs/sgp.log`, con rotación diaria y retención de 90 días.
- La salida de contenedor de los cuatro servicios rota con `json-file` (10 MB, 3 archivos), para que no llene el disco.
- Para leer la bitácora: `docker compose exec backend tail -f /app/logs/sgp.log`.

## Actualizar un entorno

Un deploy nuevo es un push (o PR) al repositorio correspondiente. El job `deploy` hace `git fetch` del commit exacto, escribe el `.env`, descarga la imagen y ejecuta el bootstrap anterior antes de `docker compose up -d --wait`. Si una fase falla, el job falla; Compose no garantiza restaurar automáticamente los contenedores anteriores.

## Respaldo

Los respaldos diarios de la base y de MinIO (`scripts/respaldo.sh`) van en un cron del host. Procedimiento completo, prueba de restauración y pasos de restauración real: [guía de respaldo y restauración](guia-respaldo-y-restauracion.md).

## Datos de demo para calificar

`scripts/datos-demo.sql` crea un Catedrático, un Estudiante y un Profesional externo verificado (`catedratico.demo`, `estudiante.demo` y `profesional.demo`, con `@sgp.gt`), un curso y un puente asignado al estudiante. Las cuentas entran con la contraseña del administrador inicial, así que ninguna clave queda en el repositorio. Correrlo otra vez no duplica nada. Las inspecciones se hacen en vivo desde la aplicación.

```sh
cd ~/puentes/stage/pf-puentes-backend
docker compose exec -T postgres psql -U puentes_migrador -d puentes -v ON_ERROR_STOP=1 < scripts/datos-demo.sql
```

## Documentación de la API

La especificación OpenAPI se publica en `/api/docs` (interfaz en `/api/docs/swagger-ui.html`). En prod exige sesión (DT-BE-12). En CI, el job de pruebas sube el JSON como artefacto `openapi`, que es el que se entrega.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| El backend no arranca y dice que no encuentra el bucket | Hay `MINIO_APP_*` cargados pero la cuenta no existe. Correr `minio-cuenta-servicio.sh` |
| El deploy a prod dice que el árbol no pasó por stage | Prod solo acepta imágenes de stage. Desplegar stage primero con ese mismo commit |
| Publicar una inspección responde `ic-no-calculable` | Falta el juego de pesos del entorno (paso 5 del primer arranque) |
| `docker compose up --wait` falla en `minio` | Revisar `docker compose logs minio`; el volumen puede tener dueño incorrecto si el entrypoint no pudo hacer `chown` |
| `Could not resolve placeholder` en el backend | Falta una variable en el `.env`. Comparar con `.env.example` |
