# P7 — API de cursos y asignaciones

Implementación: `com.cunoc.puentes.curso`. OpenAPI: `/api/docs`, etiqueta **Cursos**.

| Método | Ruta bajo `/api/v1` | Rol |
|---|---|---|
| GET/POST | `/cursos` | CATEDRATICO |
| GET/PATCH | `/cursos/{cursoId}` | CATEDRATICO propio |
| GET/POST | `/cursos/{cursoId}/inscripciones` | CATEDRATICO propio |
| GET/POST | `/cursos/{cursoId}/inscripciones/{inscripcionId}/asignaciones` | CATEDRATICO propio |
| GET | `/cursos/estudiantes-elegibles` | CATEDRATICO |
| GET | `/cursos/puentes-disponibles` | CATEDRATICO |
| GET | `/mis-asignaciones` | ESTUDIANTE propio |

Curso: `{nombre, anio, semestre, vigente}`; inscripción: `{estudiante_id}`; asignación: `{puente_id}`. GET de colecciones: `page`/`size`, máximo 100; respuesta `{content, number, size, total_elements, total_pages}`. PATCH requiere los cuatro campos del curso, no cambia el catedrático. Fechas UTC e ids UUID v7. Selectores aceptan `q` opcional de hasta 120 caracteres: nombre de estudiante o nombre/código de puente, sin distinguir mayúsculas y con `%`, `_`, `!` literales. Las inscripciones incluyen `estudiante_nombre`; las asignaciones añaden `curso_nombre`, `puente_codigo`, `puente_nombre`, sin retirar los UUID. Las etiquetas son valores actuales, no snapshots históricos.

Errores ProblemDetail: 400 validación, 401 sin sesión, 403 rol/cuenta, 404 recurso ajeno/inexistente, 409 duplicado/curso cerrado, 422 estudiante/puente no elegible, 503 activación de P2 ausente.

**Contrato 6:** `CursoService.consultarAsignacionesVigentes(UUID, Pageable)` y `CursoService.exigirAsignacionVigente(UUID estudianteId, UUID asignacionId)`. El DTO contiene curso/inscripción/estudiante/puente. P5 usa el autor autenticado; P6 recorre todas las páginas.

**Integración P2 (11/10):** seguridad JWT y EstadoCuentaService reales; mutaciones con `@Auditable`/Acciones comunes, sin aspecto/repositorio local. Login/renovación y bean `ActivacionEstudiante.activar(estudianteId, catedraticoId)` siguen pendientes. El bean debe participar en la misma transacción, validando/auditando la cuenta. Sin bean, pendientes fallan 503; activos pueden inscribirse. El frontend aún necesita AuthService de P2. No se crean tokens ni cuentas ficticias en producción.

[ADR 0016](../adr/0016-cursos-asignaciones-y-contratos-p7.md). Pruebas en `src/test/java/com/cunoc/puentes/curso/`, integración con Testcontainers y JWT real. Validación: `./mvnw spotless:check clean verify pmd:check`. `clean` evita clases antiguas tras el cambio de paquete de Auditoria durante el merge. Configurar JWT_SECRET (mínimo 32 bytes); las migraciones existentes no se modificaron.
