# ADR 0052 — M09: replay autorizado y reconciliación de baja de archivo

Estado: decisión técnica delegada por S2, implementada y verificada localmente; sin aceptación externa. Job15: 168/168 pruebas focalizadas y 1758/1758 globales sin fallos, errores ni omitidos; JaCoCo, Spotless/package/PMD y diccionario generado aprobados. RED anterior: job12 exit 1/25 s, 18 pruebas, 11 fallos, cero errores/omitidos.

## Contrato (S1–S3, T3)

`DELETE /api/v1/archivos/{id}` conserva la baja física actual. Sin clave o con blanco conserva 204 inicial y 404 posterior. No introduce purgas, vencimientos ni bajas de borradores (T4).

Con clave: `strip`, máximo 100 puntos Unicode; huella SHA-256 versionada de método DELETE y UUID canónico del recurso. El padre y la encarnación física se conservan como evidencia del intento, no se recalculan a partir de una fila nueva. Solo 204 sin cuerpo ni Content-Type se persiste. Rechazos y fallos no consumen resultado. Espera explícita de 2 segundos; conflicto `operacion-en-proceso` reintentable. Misma clave para otro recurso/método o resultado legado no verificable: `clave-idempotencia-incompatible`.

## Identidad y autorización

Una intención durable separada conserva usuario/clave/huella, UUID de archivo, UUID del padre, claves físicas exactas de objeto principal y miniatura opcional y estado de finalización. No conserva permisos. El par usuario/clave no basta para conceder acceso: la búsqueda de evidencia exige coincidencia exacta del recurso solicitado, y el padre retenido se autoriza contra su autor y vigencia actuales antes y después de esperar/reproducir. Un desconocido devuelve `archivo-no-encontrado`; pérdida posterior de acceso al padre devuelve `inspeccion-no-encontrada`.

Solo una mutación nueva exige BORRADOR. Un replay autorizado después de ENVIADA no vuelve a borrar ni tocar actividad. El replay confirmado nunca consulta la nueva encarnación para ejecutar efectos: si se reutilizó el UUID, reproduce únicamente el 204 antiguo. Una clave nueva puede borrar la nueva encarnación.

## Orden y transacciones

1. Resolver identidad exacta desde archivo actual o intención coincidente; autorizar padre sin transacción ambiente.
2. Reutilizar G7.1 para normalización compatible, exclusión usuario/clave, huella, resultado y puerto `IntencionIdempotencia`; no ampliar allowlists de carga/envío.
3. En callback, fijar `lock_timeout = '2s'`; adquirir fila de inspección y luego advisory UUID mediante `BloqueoArchivo`. Releer fila y autorización después de esperar. Mantener este orden en escritores históricos y mantenimiento.
4. Bajo esas exclusiones, registrar manifiesto en REQUIRES_NEW antes del primer DELETE externo. No tomar en esa conexión bloqueos de padre/UUID retenidos por la conexión exterior. La intención no debe depender por FK de la fila que se elimina. Reutilizar funciones de bloqueo/registro, nunca construir una reserva paralela inline.
5. Inspeccionar cada clave del manifiesto: ausencia confirmada no repite DELETE; presencia permite borrarla y confirmar ausencia; errores de transporte no equivalen a ausencia. Tras efecto incierto, retry inspecciona esas mismas claves. Nunca generar claves nuevas ni borrar por prefijo.
6. En transacción exterior, borrar fila exacta, flush, tocar actividad y guardar 204/finalizar intención atómicamente. Si el commit falla, el manifiesto sobrevive y el retry reconcilia ausencias sin repetir DELETE.
7. Reautorizar padre vigente antes de entregar resultado, también cuando G7.1 evita el callback por replay.

La reserva pendiente debe impedir que cargas con otra clave/sin clave, otro padre o mantenimiento reutilicen el UUID y que otra baja robe el manifiesto. Extender el guard común existente y el puerto de intenciones; preservar deduplicación UUID confirmada de cargas. La reserva finalizada no es una prohibición permanente de reutilizar UUID. La identidad física retenida evita que el replay antiguo borre un recurso nuevo.

## Primer corte verificable

`ArchivoBajaIdempotenciaHttpTest` activa las dos reglas M09 preservadas y prepara casos HTTP para replay vacío, permisos actuales, padre eliminado/estado, incompatibilidad entre recursos/rutas, Unicode, rechazos no cacheables, DELETE real incierto PDF/foto, rollback real del resultado, UUID reutilizado, duplicados concurrentes y compatibilidad histórica. Los snapshots incluyen filas completas, auditoría y contenido/MIME remoto.

La ampliación prepara aislamiento de misma clave entre usuarios autorizados, reincarnación bajo otro padre con pérdida de acceso únicamente al padre original y siete competidores de una baja detenida antes del efecto externo: otra clave, sin clave, blanco, foto histórica, PDF con clave, foto en otro padre y mantenimiento. La barrera externa garantiza intención durable; se observa bloqueo SQL real mediante pg_stat_activity/pg_blocking_pids antes de provocar rollback del original. El competidor debe revalidar la reserva, devolver 409 y no cambiar filas, auditoría, contadores ni bytes/MIME. No se afirma espera observada dentro del try-lock G7.1: estos competidores esperan fila o UUID. Se prepara además regresión de purga: omite reserva pendiente y conserva purga histórica después de recuperarla. La purga no tiene endpoint HTTP; se invoca su entrada pública programada.

Las pruebas adicionales de preservación no tienen RED separado ni requirieron cambios productivos en esta ampliación. La reautorización inicial usa una barrera sobre la comprobación previa y no acredita entrada al bucle try-lock. La purga histórica adquiere inspección→UUID por archivo y consulta la reserva pendiente antes de eliminar objetos; su regresión ampliada pasó.

## Evidencia final y límites

- Job15: 168/168 focalizadas, 1758/1758 globales, tres gates JaCoCo y controles Spotless/package/PMD/diccionario/diff, todos exit 0. El primer intento ampliado tuvo seis errores del fixture MVC por el nuevo bean faltante; se añadió únicamente su mock sin cambiar permisos ni expectativas.
- Independiente: 127/127, tres sondas propias, 19/19 PDF, package y generación del diccionario idéntico al candidato, exit 0; sin bloqueantes reproducidos. Las sondas comprueban manifiesto durable visible desde otra conexión antes de DELETE, pérdida de respuesta tras DELETE real, purga que conserva miniatura pendiente, recuperación sin repetir DELETE principal y competidores que revalidan tras commit sin robar manifiesto.
- Diferencial contra c5b2af9: ambos Maven exit 0, 90 observaciones por versión, sin diferencias inesperadas. Clasifica los cambios solicitados de replay keyed 404→204, incompatibilidad cruzada 204→409 y clave de 101 caracteres 204→400, incluidas limpiezas derivadas. Cinco rechazos de binding se comparan por campos completos y multiconjunto de errores, no RAW. Cada 204 se valida RAW: cuerpo vacío y Content-Type ausente.

No acredita JWT real, cambio temporal de rol en BD dentro de la misma petición, corte físico de red, transporte frontend ni aceptación global. T4/M08 y T5 permanecen fuera del cierre local T3.
