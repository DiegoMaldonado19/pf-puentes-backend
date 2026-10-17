# 0004. Configuración por entorno con GitHub Environments

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-DEP-05, DT-BE-15

## Contexto
La configuración va en un `.env` fuera de git, documentado en `.env.example`. Cada entorno necesita valores distintos para las mismas variables.

## Decisión
- Se crean los GitHub Environments `dev`, `stage` y `prod`, con **los mismos nombres** de secreto en los tres.
- Reglas de rama de cada Environment (Deployment branches and tags): `dev` sin restricción, `stage` solo `develop` y `prod` solo `main`.
- El job `deploy` declara su Environment, así que GitHub le entrega los valores de ese entorno.
- El job genera el `.env` y lo copia al clon del entorno en la EC2 (`umask 077`).
- Los valores van entre comillas simples para que Compose no interprete un `$`.

| Nivel | Nombre | Tipo | Valor |
|---|---|---|---|
| Repositorio | `EC2_HOST` | secreto | Elastic IP de la EC2 |
| Repositorio | `EC2_USER` | secreto | `ubuntu` (AMI Ubuntu) o `ec2-user` (Amazon Linux) |
| Repositorio | `EC2_SSH_KEY` | secreto | Contenido completo del `.pem` |
| Repositorio | `DOCKERHUB_USERNAME` | secreto | Usuario de Docker Hub |
| Repositorio | `DOCKERHUB_TOKEN` | secreto | PAT de Docker Hub con permiso Read & Write |
| Environment | `DB_MIGRATION_PASSWORD` | secreto | `openssl rand -hex 24` |
| Environment | `DB_APP_PASSWORD` | secreto | `openssl rand -hex 24` |
| Environment | `MINIO_ROOT_USER` | secreto | `puentes-<entorno>` |
| Environment | `MINIO_ROOT_PASSWORD` | secreto | `openssl rand -hex 24` |
| Environment | `JWT_SECRET` | secreto | `openssl rand -hex 32` |
| Environment | `MINIO_URL_PUBLICA` | secreto | `https://pf-puentes.duckdns.org:8082` (dev), `:8081` (stage), sin puerto (prod) ([0013](0013-url-firmadas-de-minio-servidas-por-nginx.md)) |
| Environment | `CORS_ALLOWED_ORIGINS` | secreto | `https://pf-puentes.duckdns.org:8082` (dev), `:8081` (stage), sin puerto (prod) |
| Environment | `ADMIN_INICIAL_CORREO` | secreto | Correo del primer administrador (RN-USR-03) |
| Environment | `ADMIN_INICIAL_NOMBRE` | secreto | Nombre completo del primer administrador |
| Environment | `FRONTEND_URL` | secreto | Mismo origen que `CORS_ALLOWED_ORIGINS`: base de los enlaces de los correos |
| Environment | `CORREO_MODO` | secreto | `smtp` (obligatorio en prod) o `consola` (solo dev: imprime los enlaces en el log) |
| Environment | `CORREO_REMITENTE`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USUARIO`, `SMTP_CLAVE` | secreto | Servidor de correo de salida; solo con `CORREO_MODO=smtp` |
| Environment | `ADMIN_INICIAL_CONTRASENA` | secreto | Mínimo 10 caracteres, con letra y dígito; `openssl rand -base64 18` sirve. Solo se usa si no existe ningún administrador |

## Consecuencias
- Un solo workflow para los tres entornos, y ningún secreto en el repo ni en la imagen.
- Todos se crean como **secretos**, no como variables del Environment: el workflow solo lee `secrets.*`, así que una variable con el mismo nombre llega vacía.
- Las contraseñas de postgres solo se aplican al crear el volumen. Para cambiarlas después hay que hacer `ALTER ROLE` ([0007](0007-usuarios-de-base-de-datos.md)).
- Agregar una variable implica tocar `.env.example`, `.env.local`, `compose.yaml`, el heredoc del workflow y los tres Environments.
- `.env.local` es la excepción a DT-DEP-05: se versiona porque solo trae valores de desarrollo local, que no son secretos.
- dev no puede limitarse a `develop`: sus deploys corren en `refs/pull/<n>/merge` y GitHub los rechaza (`Branch "refs/pull/1/merge" is not allowed to deploy to dev`). El workflow ya filtra qué PR despliega a dev (`github.base_ref == 'develop'`).
