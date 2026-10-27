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

**Integración actual (27/10):** seguridad JWT y EstadoCuentaService reales; mutaciones con `@Auditable`/Acciones comunes. Login, renovación, cierre y perfil propio existen. El frontend registra el adaptador `crearSesionCursosAuth` y reutiliza sus interceptores. `ActivadorDeEstudiante` implementa `ActivacionEstudiante` con propagación MANDATORY: valida al catedrático y al estudiante, exige correo verificado y activa la cuenta dentro de la inscripción; si esta falla, se revierte la activación. Verificar correo por sí solo no activa la cuenta. El 503 queda como protección si falta el proveedor, no como estado normal de esta versión.

[ADR 0018](../adr/0018-cursos-asignaciones-y-contratos-p7.md), renumerado por colisión con IC (0016); no se modificaron sus ADR ni el 0017 de sesiones. Pruebas en `src/test/java/com/cunoc/puentes/curso/`, integración con Testcontainers y JWT real. `CursoSesionIntegracionTest` añade el flujo con login/cookies reales, sin helper de identidad ni activación simulada. Los hashes BCrypt y contraseñas son exclusivamente de fixtures; no se imprimen cuerpos de MockMvc. La limpieza incluye solo tokens de renovación de las cuentas P7 para no dejar FK pendientes.

El cierre de sesión revoca refresh y borra cookie; el JWT emitido sigue válido hasta vencer, salvo una baja, que se verifica por petición. El frontend P2 debe limpiar su acceso en memoria. P7 verifica este comportamiento sin alterar el contrato de P2.

Validación focalizada del 27/10: `spotless:apply -Dtest=CursoSesionIntegracionTest test`, **3 pruebas correctas**, sin errores ni omitidas. Cubre rol/propiedad, inscripción con activación y renovación/cierre. No equivale a ejecutar toda la suite. La base `PruebaIntegracion` proporciona secreto JWT y conexiones efímeras; no hay que apuntar estos tests al PostgreSQL de desarrollo.

Validación completa pendiente: `./mvnw spotless:check clean verify pmd:check`. No se modificaron migraciones, wrapper ni dependencias para el nuevo test.

[Verificación del 18/10 y bloqueo de Testcontainers por Docker no disponible](verificacion-2026-10-18.md). No reutilizar los resultados históricos como validación del estado actual.
