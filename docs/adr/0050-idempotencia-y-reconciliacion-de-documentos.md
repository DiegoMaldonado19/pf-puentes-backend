# 0050. Idempotencia y reconciliación durable del PDF

- Estado: aceptado como decisión técnica delegada por S2; implementación M06 verificada localmente, no aceptación externa P1/P5.
- Fecha: 27/10/2026
- Directriz o regla relacionada: DT-OFF-07, RN-OFF-08, RN-ARC-04/05, DT-ALM-02/03, DT-CAL-09.

## Contexto

M06 deduplicaba por UUID pero reconstruía enlaces firmados y podía dejar objetos sin fila al fallar PostgreSQL. G7.1 confirma resultado y dominio en una transacción local; no puede revertir MinIO. T3 autoriza reconciliación técnica sin nuevas purgas ni cambios de negocio. Este corte implementa únicamente PDF; fotos y DELETE idempotente siguen pendientes.

## Decisión

Integrar M06 con G7.1 para admitir exclusivamente 201 JSON exacto. Registrar antes del primer PUT una intención durable independiente con identidad estable del objeto y huella del contenido; al recuperar, leer el objeto completo para verificar tamaño/MIME/SHA-256 antes de reutilizarlo. Un contenido incompatible produce 409 sin sobrescritura; un error de transporte no significa ausencia.

### Fronteras y orden

1. Sin transacción ambiente: autorizar padre propio/no eliminado, validar clave y calcular huella v1. G7.1 reclama usuario/clave durante hasta 2 s. Tras adquirir el advisory lock y antes de resultado/callback consulta el puerto genérico `IntencionIdempotencia`; una identidad durable incompatible devuelve 409 sin ejecutar otro handler. No hay dependencia del núcleo hacia archivos ni su tabla.
2. En el callback transaccional: fijar `lock_timeout` SQL de 2 s, bloquear fila de inspección y luego advisory lock del UUID de archivo. Reautorizar con lectura nueva, conservar `yaGuardado` y exigir editabilidad/cupo/formato solo para nueva alta.
3. `CargaDocumentoService.preparar`, `REQUIRES_NEW`: confirmar intención antes de MinIO desde el callback que mantiene el bloqueo usuario/clave exterior. La nueva transacción no readquiere ese bloqueo y la consulta del puerto no bloquea recursos. `READ_COMMITTED` ve esa evidencia después de esperar una ejecución que revirtió. No tiene FK a inspección/archivo/idempotencia: esas filas pueden estar sin confirmar o eliminarse y la intención debe sobrevivir sin provocar espera circular sobre el padre bloqueado.
4. Reconciliar únicamente la clave guardada. PUT si hay ausencia confirmada; leer después para verificar integridad. Si PUT responde inciertamente, devolver fallo, conservar intención y verificar al reintentar, no repetir ciegamente.
5. En la transacción G7.1: marcar intención finalizada, guardar y hacer flush de archivo antes de la actualización de actividad que limpia el contexto JPA; guardar resultado binario. El rollback final revierte finalización, fila, actividad y resultado, no la intención ni MinIO.
6. Antes de entregar bytes —también replay— volver a autorizar. No exigir BORRADOR para reproducir un resultado ya admitido, incluso tras baja del adjunto.

Los escritores históricos de archivos de inspección comparten inspección→UUID. El creador común de fotos consulta la reserva pendiente por UUID antes de escribir foto/miniatura, también para otros padres. Mantenimiento conserva orden→UUID dentro de su transacción; no toma la fila de inspección ni añade idempotencia M05. La consulta reutiliza la misma validación que la preparación PDF, sin readquirir el advisory lock en `REQUIRES_NEW`. `yaGuardado` queda antes del guard de creación: UUID confirmados conservan deduplicación histórica, incluso entre tipos. Envío adquiere la misma fila SQL mediante `ArchivoService.contarFotos`, antes de cambiar estado. Se añade a G7.1 el puerto de consulta de intenciones sin cambiar sus allowlists, formato de resultado, timeout ni autorización delegada al handler; no se reescribe el flujo de mantenimiento. Con clave distinta o sin clave, un UUID ya persistido sigue deduplicándose; si solo existe una intención pendiente de otra operación, 409 `operacion-en-proceso` hasta recuperar la operación original.

### Contrato de huella

JSON canónico de claves ordenadas: `method=POST`, `path=/api/v1/inspecciones/{UUID}/documentos`, `id` UUID canónico y objeto `pdf` con `mime` declarado (incluido null), `sha256` hexadecimal y `tamano` entero. SHA-256 binario con versión 1. Nombre del cliente y boundary no participan. Metadatos de foto, `X-Sgp-Sync` y parámetros ajenos al DTO PDF no se incorporan.

Clave opcional: sin clave/blanco conserva ruta histórica; en otro caso se aplica `strip`, máximo 100 puntos de código Unicode y alcance por usuario. Legado JSONB u otra huella: 409 `clave-idempotencia-incompatible`. Solo 201 se guarda; permisos, validación, cupo, estados, incompatibilidad y transitorios no se cachean.

## Consecuencias

- Replay conserva status y bytes originales, incluidas URLs vencidas; no equivale a lectura vigente.
- La intención sin expiración consume almacenamiento; no hay job nuevo, compensación automática ni política de purga. Si el padre pierde permisos o deja BORRADOR durante una carga pendiente, no finalizarla; conservar evidencia para una recuperación autorizada.
- Lectura completa añade tráfico MinIO y tiempo bajo bloqueo; se procesa por bloques, sin cargar otra copia completa para calcular el hash remoto. ETag no es una prueba suficiente de contenido.
- Un intento que nunca llegó a admitir respuesta no promete conservar la URL firmada calculada antes de rollback. El primer 201 confirmado es el resultado original.
- La nueva API `AlmacenamientoArchivos.inspeccionar` requiere implementaciones reales; no hay default que interprete silenciosamente una capacidad ausente como objeto inexistente.
- RED entre handlers observado por el parent: 19 casos, 1 fallo (422 en vez de 409), 0 errores y 2 omisiones M09, exit 1/31 s sin timeout. El parent acreditó posteriormente job2 GREEN, 132 casos (17 PDF), cero fallos/errores/omitidos, y job3 compartido/global/gates/diccionario exit 0 antes de B1. M09 se conserva como funciones pendientes sin selección JUnit para que el runner M06 no herede omisiones.
- B1 del verificador independiente reprodujo apropiación de UUID reservado por foto histórica y posterior admisión keyed de JPEG como respuesta PDF. La corrección limita el nuevo rechazo a reservas pendientes y añade regresión HTTP de mismo padre/otro padre/mantenimiento, recuperación PDF y preservación de UUID confirmados. Job5 aprobó la corrección: 106/106 pruebas focalizadas y 1706/1706 globales, JaCoCo, Spotless/package/PMD y diccionario sin nuevo diff. Reverificación independiente limitada B1: 3/3 sin omitidos, exit 0.
- Diferencial de 95 observaciones: 90 coinciden con exclusiones dinámicas documentadas y cinco rechazos 400 solo difieren en el orden de errores de binding; no se afirma igualdad de bytes para esos cinco. Replay 201 comprobado RAW. Guía y evidencia: [idempotencia de archivos](../idempotencia-archivos.md). No acredita M05/M09, JWT real, corte físico de respuesta ni aceptación integral V06/V13.
