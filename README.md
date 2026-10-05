# pf-puentes-backend

API REST del Sistema de Gestión de Puentes (SGP).

## Estado (04/10/2026)

| Paquete | Qué hay | Responsable |
|---|---|---|
| `common/` | Errores RFC 7807 (`NegocioException`), CORS, OpenAPI en `/api/docs`, ShedLock | P1 |
| `archivo/` | Subida de fotos y PDF, borrado en borradores, URL firmadas y purga de 210 días (RN-ARC) | P1 |
| Los demás módulos | Tabla, migración y entidad del modelo base ([ADR 0010](docs/adr/0010-modelo-de-datos-base.md)); todavía sin servicios ni endpoints | Cada responsable |

- La seguridad (login JWT, `@EnableMethodSecurity`) es de P2 y todavía no existe: hoy Spring Security protege todo con su configuración por defecto.
- Los contratos entre módulos y las fechas están en `docs-analisis/Equipo`.

## Desarrollo local

Requisitos: Docker con Compose 2.32 o superior. En Windows, Docker Desktop con la integración de WSL activada.

```bash
cp .env.example .env   # completar las contraseñas: openssl rand -hex 24
docker compose -f compose.local.yaml up --watch
```

| Servicio | Dirección | Credenciales |
|---|---|---|
| API | http://localhost:8080 | — |
| PostgreSQL | `localhost:5432`, base `puentes` | `puentes_migrador` / `DB_MIGRATION_PASSWORD` · `puentes_app` / `DB_APP_PASSWORD` |
| Consola de MinIO | http://localhost:9001 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |

El backend crea el bucket `archivos` de MinIO al arrancar.

**Documentación de la API:** http://localhost:8080/api/docs/swagger-ui.html (el JSON está en `/api/docs`). Todo endpoint nuevo lleva `@Tag`, `@Operation` y un `@ApiResponse` por código.
- Mientras no exista la seguridad de P2, Spring pide iniciar sesión con el usuario `user` y la contraseña que imprime al arrancar: `docker compose -f compose.local.yaml logs backend | grep "generated security password"`.

**Hot reload:** al guardar en `src/`, la app se recompila y devtools la reinicia (unos 5 s). Si cambias `pom.xml`, se reconstruye la imagen.

**Comandos útiles**
- Salud: `docker compose -f compose.local.yaml exec backend curl -s localhost:8081/actuator/health`. El actuator escucha en el puerto 8081, que no se publica (DT-DEP-10).
- Consola SQL: `docker compose -f compose.local.yaml exec postgres psql -U puentes_migrador -d puentes`
- Apagar: `docker compose -f compose.local.yaml down`. Con `-v` también se borran la base y los archivos.

### Usuarios de base de datos

| Usuario | Lo usa | Permisos |
|---|---|---|
| `puentes_migrador` | Flyway | Dueño del esquema y superusuario, porque `CREATE EXTENSION postgis` lo exige |
| `puentes_app` | La aplicación | Solo `SELECT`, `INSERT`, `UPDATE` y `DELETE`: no puede crear ni alterar tablas |

El script `docker/postgres/01-usuario-app.sh` crea `puentes_app` solo cuando el volumen es nuevo. Si cambias su contraseña en el `.env`, recrea el volumen local (`down -v`) o haz `ALTER ROLE`. Detalle en el [ADR 0007](docs/adr/0007-usuarios-de-base-de-datos.md).

### Modelo de datos

- Cada módulo tiene su migración en `src/main/resources/db/migration` y sus entidades en su paquete ([ADR 0010](docs/adr/0010-modelo-de-datos-base.md)).
- **Nunca edites una migración fusionada:** un cambio de esquema es una migración nueva, con fecha y hora en el nombre.
- La app arranca con `ddl-auto=validate`: si tu entidad no coincide con tu tabla, falla al iniciar y te dice qué columna está mal.
- Las entidades usan Lombok (`@Getter`, `@Setter`, constructor protegido), nunca `@Data` ([ADR 0012](docs/adr/0012-lombok-en-las-entidades.md)).

### Procesos programados

Cada `@Scheduled` lleva `@SchedulerLock(name = "...")`: así una sola réplica lo ejecuta (DT-BE-14). La configuración está en `common/ProcesosProgramadosConfig` y la tabla `shedlock` ya existe ([ADR 0014](docs/adr/0014-tabla-shedlock-con-el-esquema-de-la-libreria.md)).

## Antes de abrir un PR

Son los mismos chequeos que corre el CI:

| Etapa | Comando |
|---|---|
| Formato | `./mvnw spotless:apply` (el CI corre `spotless:check`) |
| Build y análisis estático | `./mvnw -B spotless:check package pmd:check -DskipTests` |
| Pruebas y cobertura | `./mvnw -B verify` |

- PMD usa sus reglas por defecto; cada violación sale en la consola y en `target/pmd.xml`.
- `verify` necesita Docker: Testcontainers levanta PostgreSQL + PostGIS y MinIO reales. Las pruebas de integración extienden `PruebaIntegracion`, que comparte un solo contenedor de cada uno en toda la suite.
- Pruebas de rol por endpoint (DT-CAL-03): hasta que P2 entregue su helper de JWT, sigue el ejemplo de `archivo/ArchivoControllerTest` (`@EnableMethodSecurity` de prueba y `user("<uuid>").roles(...)`).
- El reporte de JaCoCo queda en `target/site/jacoco/index.html`.
- El CI falla si las clases `*Service` bajan de 70 % de líneas cubiertas (DT-CAL-01).

## Despliegue

GitHub Actions (`.github/workflows/ci-cd.yml`) corre cuatro etapas: build → test → push → deploy.

| Evento | Entorno | Carpeta en la EC2 |
|---|---|---|
| PR hacia `develop` | dev | `~/puentes/dev/pf-puentes-backend` |
| push a `develop` | stage | `~/puentes/stage/pf-puentes-backend` |
| push a `main` | prod | `~/puentes/prod/pf-puentes-backend` |

- **Imagen:** `<DOCKERHUB_USERNAME>/pf-puentes-backend:<número de build>`, más el tag `:tree-<hash>` que la identifica por contenido.
- **Promoción:** si ese código ya tiene imagen, se promueve la misma sin reconstruirla. Prod solo acepta imágenes que pasaron por stage.
- **Deploy:** el job escribe el `.env` del entorno desde su GitHub Environment y corre `docker compose up --wait`. Si un contenedor no queda sano, el job falla.
- **Acceso:** el backend, postgres y minio no publican puertos. La API se alcanza por el nginx del frontend en `https://pf-puentes.duckdns.org[:puerto]/api`, y las fotos por las URL firmadas de `/archivos/` ([ADR 0013](docs/adr/0013-url-firmadas-de-minio-servidas-por-nginx.md)).
- **Revisar un entorno:** `cd ~/puentes/<entorno>/pf-puentes-backend && docker compose ps`

Las decisiones están en [docs/adr](docs/adr/). Los secretos y variables por configurar están en el [ADR 0004](docs/adr/0004-configuracion-por-entorno-con-github-environments.md).

## Problemas conocidos

**La app no arranca: `Could not resolve placeholder '...'`**

A tu `.env` le falta una variable que se agregó después de que lo creaste. Copia la línea que falta desde `.env.example`.

**Maven falla en WSL con `bad_record_mac` o `Tag mismatch`**

En algunas instalaciones, la red de WSL corrompe las descargas HTTPS grandes. Dentro de Docker no pasa, así que `compose.local.yaml` funciona igual.

Para Maven en el host, baja las dependencias desde un contenedor y luego usa `./mvnw` normal:

```bash
docker run --rm -u "$(id -u):$(id -g)" -v "$PWD":/app -v "$HOME/.m2":/var/maven/.m2 \
  -e MAVEN_CONFIG=/var/maven/.m2 -w /app maven:3.9.16-eclipse-temurin-25 \
  mvn -B -Duser.home=/var/maven dependency:go-offline
```

## Tecnologías y versiones

**Lenguaje y build**

| Tecnología | Versión |
|---|---|
| Java (Eclipse Temurin) | 25.0.4 |
| Maven (wrapper) | 3.9.16 |

**Framework y dependencias**

| Dependencia | Versión |
|---|---|
| Spring Boot (actuator, data-jpa, flyway, security, validation, webmvc, devtools) | 4.1.1 |
| Spring Framework | 7.0.9 |
| Tomcat embebido | 11.0.24 |
| Hibernate ORM + hibernate-spatial | 7.4.5.Final |
| hypersistence-utils-hibernate-73 | 3.16.0 |
| Jackson | 3.1.5 |
| Flyway (+ flyway-database-postgresql) | 12.4.0 |
| PostgreSQL JDBC | 42.7.13 |
| springdoc-openapi-starter-webmvc-ui | 3.1.0 |
| MapStruct | 1.6.3 |
| JJWT (api, impl, jackson) | 0.13.0 |
| AWS SDK for Java v2 (s3), cliente de MinIO | 2.55.11 |
| ShedLock (spring, provider-jdbc-template) | 7.10.1 |
| Lombok (+ lombok-mapstruct-binding 0.2.0) | 1.18.46 |

**Pruebas y calidad**

| Herramienta | Versión |
|---|---|
| JUnit Jupiter | 6.0.3 |
| Mockito | 5.23.0 |
| Testcontainers (postgresql, minio) | 2.0.5 |
| JaCoCo | 0.8.15 |
| Spotless (google-java-format 1.36.1) | 3.10.3 |
| maven-pmd-plugin (PMD 7.17.0) | 3.28.0 |

**Imágenes Docker**

| Imagen | Uso |
|---|---|
| `maven:3.9.16-eclipse-temurin-25` | Compilación y desarrollo local |
| `eclipse-temurin:25.0.4_7-jre-alpine` | Runtime del backend |
| `postgis/postgis:17-3.6-alpine` | PostgreSQL 17 + PostGIS 3.6 (también en Testcontainers) |
| `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` | Almacenamiento de objetos ([ADR 0008](docs/adr/0008-imagenes-base-con-version-fija.md)) |

**GitHub Actions**

| Action | Versión |
|---|---|
| `actions/checkout` | v7.0.1 |
| `actions/setup-java` | v6.0.1 |
| `actions/upload-artifact` | v7.0.1 |
| `docker/login-action` | v4.6.0 |
| `docker/setup-buildx-action` | v4.4.1 |
| `docker/build-push-action` | v7.4.0 |
