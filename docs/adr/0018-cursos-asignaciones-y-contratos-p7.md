# 0018. Cursos, asignaciones y contratos de integración de P7

- Estado: aceptado para esta implementación; modelo académico pendiente de confirmación (#7)
- Fecha: 07/10/2026
- Actualización: 11/10/2026, integración de seguridad/auditoría de P2 y búsqueda/etiquetas
- Actualización: 18/10/2026, integración de sesión P7 y pruebas con login real. Renumerado de 0016 a 0018 por colisión con IC; se preservan ADR 0016 de IC y 0017 de P2.
- Reglas/directrices: RN-USR-02, RN-INS-14, DT-ARQ-02/03/04, DT-SEC-03/04, DT-BD-12, contrato 6

## Contexto

Existen curso/inscripcion/asignacion. Al 07/10 faltaba la infraestructura de P2; el merge del 11/10 incorpora JWT, EstadoCuentaService y auditoría general. Al 18/10 P2 ya ofrece login, renovación, cierre y perfil propio por HTTP, pero aún faltan AuthService/interceptores Angular, activación y los servicios de P3/P5. El contrato 6 necesita contexto académico no ambiguo. La activación y la autenticación pertenecen a Usuarios.

## Decisión

- Reutilizar tablas/entidades, UUID v7, MapStruct, errores y Testcontainers. No modificar migraciones existentes.
- Usar nombre/año/semestre/catedrático y vigencia manual del modelo base; confirmar pendiente #7 con el catedrático.
- Leer referencias de usuario/puente por proyecciones SQL en repositorios, sin importar sus entidades, conforme a ADR 0010.
- Bloquear curso propio en escrituras y cuenta del estudiante al inscribir. Mantener UNIQUE como garantía de integridad y comprobar duplicados para respuestas 409.
- Exponer consulta paginada y `exigirAsignacionVigente(estudianteId, asignacionId)` para P5. Devolver curso, inscripción y puente; no inferir un curso a partir de autor/puente.
- Definir el puerto `ActivacionEstudiante` para P2, con participación en la misma transacción. Si no existe y la cuenta está pendiente, fallar 503; no activar con SQL desde P7. Estudiantes activos pueden inscribirse.
- Reutilizar ConfiguracionSeguridad/FiltroJwt de P2; retirar SeguridadCursoConfig. Consultar rol/estado vigente mediante EstadoCuentaService y mantener la comprobación de propiedad en P7.
- Reemplazar el advisor/repositorio de auditoría provisional P7 por `@Auditable` de P2. Usar Acciones.CREAR/MODIFICAR y el id de entidad/retorno; no escribir directamente en la tabla ni ampliar el catálogo para acciones equivalentes. Se registra la fila JPA antes/después según el saneamiento central, incluido el nombre del curso, con identidad, IP y correlación compartidas. Operación y bitácora se revierten juntas.
- Añadir nombres/códigos a los DTO sin retirar los UUID. Resolver referencias de estudiante/puente en lotes de hasta 100, no una consulta por fila. Las etiquetas son los valores actuales; los identificadores conservan el contexto académico, pero no sustituyen la referencia histórica que P5 debe persistir.
- Ofrecer `q` opcional de hasta 120 caracteres en selectores paginados: nombre de estudiante y nombre/código de puente. SQL parametrizado, búsqueda literal sin distinguir mayúsculas, escapando `%`, `_` y `!`; no buscar por correo ni exponer credenciales.
- Preparar el puerto frontend `IntegracionSesionCursos` con sesión reactiva, restauración delegada y ruta de acceso suministradas por P2. P7 espera la restauración antes de habilitar vistas y limpia su estado ante cierre/cambio de identidad o errores de acceso definitivos. No implementa AuthService ni interceptores. Con el puerto registrado, CursosApi deja Authorization a P2; se conserva el proveedor inicial únicamente como compatibilidad.

## Consecuencias

P5/P6 pueden consumir contrato 6 independientemente de sus servicios de captura/sync. Obtener/renovar tokens por HTTP ya está disponible; queda pendiente que P2 entregue y registre su sesión/interceptores Angular. Sin esa integración las rutas privadas siguen cerradas. La activación productiva sigue pendiente y P2 la audita. Las pruebas P7 usan login y cookies reales en MockMvc, con BCrypt exclusivamente en fixtures; no se crean cuentas ni claves ficticias en producción. El cierre revoca la renovación, no el JWT ya emitido: el frontend de P2 debe borrar su acceso en memoria (ADR 0017). La relación histórica inspección–curso se acuerda con P5 en una integración posterior.

No se añaden dependencias ni migraciones. Los tests comprueban JWT real, baja inmediata, rollback de auditoría/activación, búsquedas y privacidad. Ejecutar `clean verify` después del merge: la entidad Auditoria se movió de paquete y una clase compilada antigua en target puede producir un falso error de entidad duplicada.
# Actualización de integración — 27/10/2026

El adaptador productivo `usuario/ActivadorDeEstudiante` ya implementa `ActivacionEstudiante` dentro de la transacción de inscripción, con validación de rol/estado/correo y auditoría compartida. Las notas de ausencia del proveedor describen el estado histórico. `CursoSesionIntegracionTest` verifica autenticación real, activación al inscribir y renovación/cierre: 3 pruebas aprobadas en ejecución focalizada, no una certificación de toda la suite.
