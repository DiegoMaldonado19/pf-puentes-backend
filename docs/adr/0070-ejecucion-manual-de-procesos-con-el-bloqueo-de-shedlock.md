# 0070. Ejecución manual de procesos con el bloqueo de ShedLock

- Estado: aceptado
- Fecha: 11/11/2026
- Revisa: ADR 0036 (recordatorios de programación rutinaria), ADR 0014 (tabla shedlock)

## Contexto
Las alertas de inspección vencida se generan a las 06:00 cada día. Si el administrador
necesitaba verlas antes (por ejemplo, tras cargar datos), no tenía forma de dispararlas. Un
disparo manual que no respetara el bloqueo podía correr a la vez que la ejecución programada
o la de otra réplica.

## Decisión
- `POST /api/v1/procesos/{nombre}/ejecucion`, solo administrador. El catálogo está cerrado;
  hoy contiene `alertas-inspeccion`. Un nombre desconocido responde 404.
- El proceso toma el mismo bloqueo distribuido que su programación
  (`alertas-inspeccion-vencida`) con `LockProvider`. Si el bloqueo ya está tomado, responde
  409 `proceso-en-ejecucion`; no omite la petición en silencio.
- La tarea se ejecuta con `AlertasInspeccionJob.ejecutarSinBloqueo()`, el cuerpo del proceso
  sin la anotación `@SchedulerLock`, para no anidar dos bloqueos con el mismo nombre.
- El bloqueo se libera en `finally`, también si la tarea falla.
- La ejecución se registra en la bitácora con la acción `EJECUTAR_PROCESO`, entidad `proceso`
  y un identificador estable derivado del nombre. La anotación `@Auditable` exige una entidad
  JPA, así que se usa `RegistroManualAuditoria`, una fachada mínima sobre `AuditoriaService`.

## Consecuencias
- Un administrador puede revisar alertas en cualquier momento sin arriesgar una ejecución
  doble.
- Los procesos nuevos se añaden al catálogo con su bloqueo y su tiempo máximo; no hay
  ejecución libre por nombre.
