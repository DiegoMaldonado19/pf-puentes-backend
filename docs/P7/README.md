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

**Integración P2 (18/10):** seguridad JWT y EstadoCuentaService reales; mutaciones con `@Auditable`/Acciones comunes, sin aspecto/repositorio local. Ya existen `POST /api/v1/auth/login`, `/renovar`, `/cerrar-sesion` y `GET /api/v1/usuarios/me`. El frontend aún necesita AuthService/interceptores de P2; P7 prepara su puerto consumidor sin implementarlos. Sigue pendiente el bean `ActivacionEstudiante.activar(estudianteId, catedraticoId)`: debe participar en la misma transacción, validando/auditando la cuenta. Sin bean, pendientes fallan 503; activos pueden inscribirse. Verificar correo no activa la cuenta. No se crean tokens ni cuentas ficticias en producción.

[ADR 0018](../adr/0018-cursos-asignaciones-y-contratos-p7.md), renumerado por colisión con IC (0016); no se modificaron sus ADR ni el 0017 de sesiones. Pruebas en `src/test/java/com/cunoc/puentes/curso/`, integración con Testcontainers y JWT real. `CursoSesionIntegracionTest` añade el flujo con login/cookies reales, sin helper de identidad ni activación simulada. Los hashes BCrypt y contraseñas son exclusivamente de fixtures; no se imprimen cuerpos de MockMvc. La limpieza incluye solo tokens de renovación de las cuentas P7 para no dejar FK pendientes.

El cierre de sesión revoca refresh y borra cookie; el JWT emitido sigue válido hasta vencer, salvo una baja, que se verifica por petición. El frontend P2 debe limpiar su acceso en memoria. P7 verifica este comportamiento sin alterar el contrato de P2.

Validación: `./mvnw spotless:check clean verify pmd:check`. `clean` evita clases antiguas tras el cambio de paquete de Auditoria durante el merge. Configurar JWT_SECRET (mínimo 32 bytes); no se modificaron migraciones, wrapper ni dependencias.

[Verificación del 18/10 y bloqueo de Testcontainers por Docker no disponible](verificacion-2026-10-18.md). No reutilizar los resultados históricos como validación del estado actual.
