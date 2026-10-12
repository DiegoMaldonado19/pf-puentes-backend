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

Curso: `{nombre, anio, semestre, vigente}`; inscripción: `{estudiante_id}`; asignación: `{puente_id}`. GET de colecciones: `page`/`size`, máximo 100; respuesta `{content, number, size, total_elements, total_pages}`. PATCH requiere los cuatro campos del curso, no cambia el catedrático. Fechas UTC e ids UUID v7.

Errores ProblemDetail: 400 validación, 401 sin sesión, 403 rol/cuenta, 404 recurso ajeno/inexistente, 409 duplicado/curso cerrado, 422 estudiante/puente no elegible, 503 activación de P2 ausente.

**Contrato 6:** `CursoService.consultarAsignacionesVigentes(UUID, Pageable)` y `CursoService.exigirAsignacionVigente(UUID estudianteId, UUID asignacionId)`. El DTO contiene curso/inscripción/estudiante/puente. P5 usa el autor autenticado; P6 recorre todas las páginas.

**Integración P2:** bean `ActivacionEstudiante.activar(estudianteId, catedraticoId)` en la misma transacción, validando/auditando la cuenta. Sin bean, pendientes fallan 503; activos pueden inscribirse. JWT de P2 debe aportar UUID como principal y roles `ROLE_*`. Aspecto de auditoría local P7 registra mutaciones; consolidarlo con el aspecto general de P2 sin duplicar registros.

[ADR 0015](../adr/0015-cursos-asignaciones-y-contratos-p7.md). Pruebas en `src/test/java/com/cunoc/puentes/curso/`, integración con Testcontainers. Validación: `./mvnw spotless:check verify pmd:check`.
