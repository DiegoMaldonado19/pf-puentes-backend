# 0024. Ajuste justificado de prioridad de órdenes

- Estado: propuesto para revisión; regla interna implementada, fachada/API pendientes
- Fecha: 19/10/2026
- Historia: HU-P8-MTO-07, parte de ajuste manual
- Reglas/directrices: RN-MTO-06; RN-USR-09; DT-BE-10; DT-SEC-04

## Contexto

La tabla distingue `prioridad_sugerida`, `prioridad` y `justificacion_prioridad`. Su CHECK permite una justificación vacía; RN-MTO-06 exige justificar un cambio. La fórmula de sugerencia usando IC/peso, el tratamiento de insumos ausentes y los permisos de modificación siguen pendientes de acuerdo. Se puede implementar la validación del ajuste sin decidir esos asuntos.

## Decisión

`ReglasPrioridadOrden` valida una prioridad vigente y una nueva prioridad. Cambiar el valor vigente requiere justificación no nula ni blanca, incluso al volver al valor sugerido; se conserva así la razón de cada cambio real. Una prioridad nueva ausente o una justificación insuficiente producen `NegocioException` 422. Una entidad aún sin prioridad vigente es una entrada interna inválida: ajustar no sustituye la inicialización de la orden.

`OrdenMantenimiento.ajustarPrioridad` valida antes de modificar y conserva intacta la sugerencia original, el estado y la intervención. Repetir la prioridad vigente no modifica la justificación anterior: este método ajusta prioridades, no edita motivos de decisiones previas. No recorta ni sustituye silenciosamente el texto recibido. Si la sugerencia almacenada es null, sigue null: un ajuste manual no inventa una sugerencia ni un IC.

Los tres setters de prioridades/justificación quedan con visibilidad de paquete para inicialización interna; no se exponen a controladores u otros módulos. JPA usa acceso a campos y el mapper de lectura continúa funcionando. Esta restricción no impide que código del propio paquete omita la regla: la futura fachada debe invocar el método validado.

## Consecuencias e integración pendiente

La regla se usa desde la entidad real y persiste en las columnas existentes; no requiere migración ni cambia el contrato JSON de lectura. No se agregan umbrales, cálculo automático, restricciones por estado ni permisos supuestos.

No existe todavía un servicio público de ajuste ni una ruta HTTP. La futura fachada deberá autorizar rol y acceso a la orden/puente, cargar la entidad, invocar `ajustarPrioridad` y guardar dentro de una transacción auditada mediante el aspecto de P2. La regla pura y el repositorio no registran por sí solos actor ni historial de auditoría. El frontend deberá mostrar la sugerida y exigir justificación al cambiar la vigente cuando exista esa API autorizada.

Las pruebas unitarias cubren los seis cambios entre prioridades, repetición sin pérdida de justificación, null/blancos, conservación y retorno a la sugerida. Las pruebas PostgreSQL comprueban el ajuste persistido y que un rechazo no deja cambios pendientes para el flush de una entidad gestionada. Verificación global: `./mvnw -B -ntp clean spotless:check verify pmd:check` con JDK 25; 580 casos aprobados (21 nuevos), sin errores, fallos ni omisiones. Pasaron formato, PMD, build y las tres reglas JaCoCo. El validador y la entidad alcanzaron 100 % de líneas y ramas. El frontend no cambia en esta entrega.
