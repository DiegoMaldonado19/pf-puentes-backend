# 0025. Aceptación autorizada y auditada de propuestas

- Estado: propuesto para revisión; aceptación backend implementada, integración y frontend pendientes
- Fecha: 19/10/2026
- Historia: HU-P8-MTO-03, operación de aceptación
- Reglas/directrices: RN-MTO-01/02/04; RN-USR-09; DT-ARQ-02/04; DT-BE-03/07/10/12; DT-BD-12; DT-SEC-03/04; DT-CAL-02/03

## Contexto

RN-MTO-02 autoriza expresamente al Catedrático y al Administrador para aceptar propuestas. No es necesario esperar a definir los actores de inicio/cierre/cancelación para implementar esa operación. El enunciado no prescribe una relación orden → curso ni una restricción de aceptación basada en ella; no se añade tal relación por extrapolar las reglas de revisión académica. El origen de la orden sí debe corresponder al puente solicitado.

## Decisión

`POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/aceptacion`, sin cuerpo, acepta exclusivamente una PROPUESTA y devuelve `OrdenMantenimientoDTO` con estado PROGRAMADA y claves snake_case. Conserva fecha programada existente, prioridades, justificación e intervención; no crea propuestas ni introduce campos obligatorios o restricciones temporales no especificadas. Aceptar no acredita por sí solo la programación anual de RN-MTO-07 ni implementa el formulario para definir una fecha.

Controlador y `OrdenMantenimientoService.aceptar` exigen CATEDRATICO o ADMINISTRADOR mediante `@PreAuthorize`. La seguridad global sigue exigiendo una cuenta activa para peticiones JWT. El servicio busca la fila por ambos IDs y comprueba su origen mediante `ValidacionOrigenOrden`: una orden inexistente o de otro puente produce 404, y una inspección incompatible, 422. Esta pertenencia no se presenta como una restricción académica por curso.

La transacción bloquea la fila con `PESSIMISTIC_WRITE` antes de entrar a un segundo bean, `AceptacionOrdenService`, que invoca la única puerta de cambio de estado. Este paso interno usa `@Auditable(CAMBIAR_ESTADO)`, con la transacción de la fachada. Bloquear antes de la foto anterior del aspecto evita tomar una instantánea previa a una modificación concurrente. No se escribe directamente en la bitácora ni se modifica código de P2. La actualización JPA y su auditoría se confirman juntas o se revierten juntas.

Tras el flush del aspecto, la fachada mapea la entidad a DTO mediante MapStruct; incluye la marca de actualización ya persistida. Repetir o competir por una aceptación deja una sola transición y una sola auditoría: el segundo intento encuentra PROGRAMADA y recibe 409. No se transforma una repetición en otro cambio válido ni se habilitan estados terminales.

## Consecuencias

Es la primera API de mantenimiento. No se habilitan lectura general, inicio, cierre, descarte, cancelación o ajuste HTTP de prioridad por inferir sus permisos de esta autorización. No hay migración ni datos de demostración en producción. La generación de propuestas depende todavía del catálogo acordado con el catedrático y de la publicación de P5.

El frontend permanece pendiente de la sesión/guardias de P2 y de la bandeja integrada de propuestas. Los datos que deba exigir el formulario de programación se resolverán como un incremento separado; este endpoint conserva los ya registrados.

## Verificación

Pruebas HTTP y PostgreSQL sin transacción de prueba para verificar commits/rollbacks: dos roles autorizados, otros roles rechazados, 401, IDs inválidos, pertenencia a puente, inspección incompatible, todos los estados no aceptables, repetición y conservación del DTO. Se comprueban auditoría anterior/posterior, rollback si falla la auditoría y dos aceptaciones concurrentes. Verificación global: `./mvnw -B -ntp clean spotless:check verify pmd:check` con JDK 25; 602 casos aprobados (22 nuevos), sin errores, fallos ni omisiones. Pasaron formato, PMD, build y las tres reglas JaCoCo. Los dos servicios nuevos alcanzan 100 % de líneas y ramas donde existen. No hubo cambios de frontend en esta entrega.
