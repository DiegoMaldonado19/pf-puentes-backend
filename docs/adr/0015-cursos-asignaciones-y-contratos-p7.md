# 0015. Cursos, asignaciones y contratos de integración de P7

- Estado: aceptado para esta implementación; modelo académico pendiente de confirmación (#7)
- Fecha: 07/10/2026
- Reglas/directrices: RN-USR-02, RN-INS-14, DT-ARQ-02/03/04, DT-SEC-03/04, DT-BD-12, contrato 6

## Contexto

Existen curso/inscripcion/asignacion, pero no servicios de P2/P3/P5. El contrato 6 necesita contexto académico no ambiguo. La activación pertenece a Usuarios y la auditoría general de P2 todavía no está disponible.

## Decisión

- Reutilizar tablas/entidades, UUID v7, MapStruct, errores y Testcontainers. No modificar migraciones existentes.
- Usar nombre/año/semestre/catedrático y vigencia manual del modelo base; confirmar pendiente #7 con el catedrático.
- Leer referencias de usuario/puente por proyecciones SQL en repositorios, sin importar sus entidades, conforme a ADR 0010.
- Bloquear curso propio en escrituras y cuenta del estudiante al inscribir. Mantener UNIQUE como garantía de integridad y comprobar duplicados para respuestas 409.
- Exponer consulta paginada y `exigirAsignacionVigente(estudianteId, asignacionId)` para P5. Devolver curso, inscripción y puente; no inferir un curso a partir de autor/puente.
- Definir el puerto `ActivacionEstudiante` para P2, con participación en la misma transacción. Si no existe y la cuenta está pendiente, fallar 503; no activar con SQL desde P7. Estudiantes activos pueden inscribirse.
- Habilitar seguridad por método y comprobar cuenta/propiedad en servicios; autenticación JWT sigue a cargo de P2.
- Auditar solo las mutaciones P7 con un advisor Spring AOP y TransactionTemplate: operación y registro comparten transacción. Se usa la tabla `auditoria`, con UUID v7 y solo identificadores/estado académico. El nombre del curso se excluye para evitar texto personal libre.

## Consecuencias

P5/P6 pueden consumir contrato 6 independientemente de sus servicios de captura/sync. El flujo real de activación y JWT queda bloqueado por P2, explícitamente. Su futuro aspecto general debe reemplazar el local para evitar auditoría duplicada; P2 audita la activación de usuario. Consolidar también la habilitación de seguridad por método. La relación histórica inspección–curso se acuerda con P5 en una integración posterior.

El advisor usa infraestructura AOP existente de Spring, sin agregar librería de estados ni dependencias nuevas. Los tests de integración comprueban rollback y privacidad de la bitácora.
