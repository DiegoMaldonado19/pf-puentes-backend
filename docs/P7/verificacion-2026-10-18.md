# P7 — Verificación posterior al pull, 18/10/2026

## Alcance

- Nuevo `CursoSesionIntegracionTest`: login real P2 → crear curso → inscribir estudiante activo → asignar puente → consulta propia; separación por rol/propiedad; acceso vencido, renovación por cookie, cierre y baja; pendientes sin activación productiva.
- Sin cambios de producción en Usuarios ni Cursos; no se implementaron AuthService, activación o emisión alternativa de tokens. No se modificaron migraciones, dependencias ni wrapper.
- Fixtures BCrypt exclusivamente de prueba, MockMvc sin impresión de credenciales y limpieza de refresh limitada a las cuentas P7.
- ADR P7 renumerado de 0016 a [0018](../adr/0018-cursos-asignaciones-y-contratos-p7.md); se preservan los de IC y P2.

## Limitación de integración

Chequeos completados con Java 25:

| Chequeo | Resultado |
|---|---|
| Compilación de fuentes y tests | Correcta, incluida la clase nueva de sesión |
| `./mvnw -B -ntp spotless:check` | Correcto |
| `./mvnw -B -ntp -Dtest=CursoServiceTest test pmd:check` | **BUILD SUCCESS**: 2 pruebas unitarias correctas y PMD sin infracciones |
| Suite completa `clean verify pmd:check` | Bloqueada por Docker/Testcontainers; no aprobada |

Se intentó `./mvnw -B -ntp clean verify pmd:check`. La compilación de fuentes y tests terminó, pero la suite de integración **no pudo ejecutarse**: Testcontainers no encuentra un daemon Docker. Faltan `/var/run/docker.sock` y el socket del contexto Desktop; apuntar DOCKER_HOST al contexto configurado tampoco lo resolvió. No se cambió la configuración global ni se inició/reconfiguró Docker.

No se declara aprobada la suite completa ni se publica cobertura actual: los números del 11/10 son históricos. En particular, las cuatro pruebas nuevas de sesión están pendientes de ejecución contra PostgreSQL/MinIO reales.

## Reproducción pendiente

Con Docker Desktop/Engine disponible, comprobar primero `docker info`. Luego ejecutar:

```bash
./mvnw -B -ntp spotless:check clean verify pmd:check
```

Si Testcontainers no resuelve el contexto Desktop automáticamente, suministrar el socket existente mediante DOCKER_HOST en el proceso de Maven, sin cambiar archivos del proyecto:

```bash
DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./mvnw -B -ntp spotless:check clean verify pmd:check
```

La autenticación automática de extremo a extremo en Angular sigue pendiente del AuthService/interceptores de P2, incluso después de aprobar esta suite backend.
