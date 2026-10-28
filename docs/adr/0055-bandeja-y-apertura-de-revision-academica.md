# 0055. Bandeja y apertura transaccional de revisión académica

- Estado: aceptado para este incremento.
- Fecha: 27/10/2026.
- Trazabilidad: RN-REV-01/03, DT-SEC-04, RN-USR-09 y contrato de Inspecciones.

## Decisión

La bandeja usa el `curso_id` persistido en la inspección. Comprueba responsable del curso, matrícula del autor, rol de Estudiante y exclusión del propio revisor; no deduce responsabilidad por compartir puente. No requiere curso vigente para permitir revisar envíos históricos. Excluye borradores, inspecciones eliminadas y estados terminales. Filtra por ENVIADA/EN_REVISION y pagina con máximo 100, orden estable por envío e identificador. No expone respuestas ni calificación.

`POST /api/v1/inspecciones/{inspeccionId}/revisiones` bloquea la fila autorizada de inspección antes de comprobar estado/ciclo activo. Guarda una nueva Revision y delega la transición exclusivamente a `PublicacionInspeccionService.abrirRevision`. Ciclo, transición y auditoría se confirman en la misma transacción. La segunda apertura devuelve 409, no crea otro ciclo ni se reintenta automáticamente.

El bloqueo serializa las aperturas hechas por este servicio; no sustituye las garantías que deberán adoptar las futuras operaciones de veredicto. No se modifican migraciones fusionadas ni se escribe directamente el estado de inspección.

## Alcance y consecuencias

Implementa bandeja y apertura, no evidencia, veredictos, calificación, observaciones, historial ni job. Aprobación futura deberá usar `publicarPorAprobacion` (directamente o mediante `emitirVeredicto`), nunca un UPDATE de estado. El frontend muestra el ciclo iniciado sin fingir que ya existe el editor de revisión.

Pruebas: fixtures acotados con limpieza por FK, autorización HTTP y de servicio, concurrencia y rollback frente a fallo de auditoría. Los resultados de ejecución se documentan en la guía del módulo, no se presume la cobertura de toda la suite.
