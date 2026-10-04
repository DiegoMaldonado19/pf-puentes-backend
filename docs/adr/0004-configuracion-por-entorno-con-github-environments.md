# 0004. Configuración por entorno con GitHub Environments

- Estado: aceptado
- Fecha: 03/10/2026
- Directriz o regla relacionada: DT-DEP-05, DT-BE-15

## Contexto
La configuración va en un `.env` fuera de git, documentado en `.env.example`. Cada entorno necesita valores distintos para las mismas variables.

## Decisión
- Se crean los GitHub Environments `dev`, `stage` y `prod`, con **los mismos nombres** de secreto en los tres.
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
| Environment | `CORS_ALLOWED_ORIGINS` | secreto | `http://<EC2_HOST>:8082` (dev), `:8081` (stage), sin puerto (prod) |

## Consecuencias
- Un solo workflow para los tres entornos, y ningún secreto en el repo ni en la imagen.
- Las contraseñas de postgres solo se aplican al crear el volumen. Para cambiarlas después hay que hacer `ALTER ROLE` ([0007](0007-usuarios-de-base-de-datos.md)).
- Agregar una variable implica tocar `.env.example`, `compose.yaml`, el heredoc del workflow y los tres Environments.
