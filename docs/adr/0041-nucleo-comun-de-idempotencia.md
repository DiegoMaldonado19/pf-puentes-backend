# 0041 — Núcleo común de idempotencia

Fecha: 26/10/2026 (UTC). Estado: implementado en el alcance G7.1. Trazabilidad: G7.1/S1–S5 del plan P6; decisiones de clave, huella, replay y concurrencia registradas en `contratos-g6.md`.

## Contexto y límites

La tabla `idempotencia` y su restricción `UNIQUE (usuario_id, clave)` ya existen, pero no había servicio común. El núcleo debe servir a futuros integradores sin acoplarse a rutas ni DTO. No habilita handlers HTTP ni acredita V06/V13, ni resuelve las coordinaciones de P1/P5.

La política de P6 exige huella SHA-256 versionada, serialización por usuario/clave, replay exacto y ausencia de expiración automática. La forma canónica y la versión que use cada recurso, el límite numérico de espera en cada integración, su allowlist y los rechazos terminales cacheables siguen pendientes para G7.2.

## Decisión

`IdempotenciaService.ejecutar` exige identidad de usuario, clave, `HuellaIdempotencia` (versión positiva y SHA-256 de 32 bytes), una `Duration` positiva explícita y un callback que devuelva `RespuestaIdempotente`. La respuesta valida status HTTP 100–599 y conserva el cuerpo como bytes sin interpretar JSON, normalizar whitespace ni distinguir formatos. Huella y cuerpo se copian defensivamente. Una respuesta vacía es distinta de `NULL` y se conserva.

El integrador controla autenticación/autorización antes de invocar el núcleo y decide qué resultados de negocio terminales admite devolviendo una respuesta desde el callback. Fallos no admitidos se expresan lanzando una excepción; esa excepción revierte tanto los cambios PostgreSQL del callback como el resultado. El núcleo no decide permisos, canonicalización, allowlists, rechazos cacheables ni reglas de payload.

## Reserva, concurrencia y transacción

Cada ejecución usa un `TransactionTemplate` con aislamiento `READ_COMMITTED` y `PROPAGATION_REQUIRED`. Si hay una transacción ambiente, falla rápido en vez de dividir la operación con `REQUIRES_NEW`. PostgreSQL y el resultado se confirman juntos en la misma transacción.

Antes de leer o ejecutar se toma un advisory lock transaccional de PostgreSQL derivado por SHA-256 de usuario + longitud + bytes UTF-8 de la clave. El lock de 64 bits puede colisionar y serializar claves distintas, pero no es la identidad: tras obtenerlo, el servicio consulta la fila real por usuario y clave y la restricción única existente sigue siendo autoritativa. `pg_try_advisory_xact_lock` se sondea hasta el `Duration` que suministra esa llamada. Al vencer, la transacción se revierte primero y luego se devuelve 409 `operacion-en-proceso`; no se crea ni se cachea una reserva incompleta. El cliente/integrador elige el valor concreto, por lo que el núcleo no introduce una espera predeterminada.

La fila solo se inserta después de que el callback haya devuelto explícitamente el resultado admitido. Status y bytes se guardan en sus columnas, manteniendo la respuesta JSONB heredada separada. El repositorio lee una proyección de solo metadatos y bytes para no deserializar `respuesta` al inspeccionar filas heredadas. La entidad conserva ese JSONB como `Object`, admitiendo valores nulos y escalares además de objetos. Una clave sin fila permite ejecutar; una fila moderna con igual versión y digest reproduce status y bytes sin volver a invocar el callback; otra huella/version produce 409 `clave-idempotencia-incompatible` sin sustituir el resultado.

## Migración y filas heredadas

La migración aditiva agrega `huella_version`, `huella_sha256` y `respuesta_bytes`, todos nullable para conservar filas existentes; una restricción exige las tres columnas vacías para filas heredadas o metadatos completos para resultados nuevos, incluyendo digest de 32 bytes. Cada columna nueva tiene comentario SQL. Las filas heredadas conservan su `estado_http` y JSONB nullable, escalar u objeto sin bytes/huella inventados. Como no se puede demostrar su huella ni reconstruir el cuerpo exacto a partir de JSONB, reutilizar su clave se rechaza con 409 `clave-idempotencia-incompatible`; nunca se vuelve a ejecutar esa clave.

No se añade expiración, purga, canonicalizador, allowlist ni mecanismo adicional de retención. El diccionario de datos se regenera con `DiccionarioDeDatosTest`, ejecutado también dentro de `./mvnw clean verify`; se versiona junto con la migración.

## Coexistencia con P5 tras el merge

La tabla conserva dos representaciones deliberadamente distintas. Las filas P5 existentes guardan el DTO de `Inspeccion` como objeto JSONB, estado HTTP 200 y las columnas de huella/bytes en `NULL`. El núcleo G7.1 guarda versión y digest de huella, estado HTTP y bytes exactos; su JSONB `respuesta` queda en `NULL`. `IdempotenciaRepository` expone ambas lecturas: la proyección de metadatos/bytes del núcleo y el finder más `save` usados por P5. `Idempotencia.respuesta` permanece como `Object` para conservar la compatibilidad con valores JSONB heredados, incluidos escalares y `NULL`.

Los lectores de replay de PUT de avance y POST de envío de P5 aceptan únicamente una fila legacy cuyo status y forma correspondan a un DTO P5 reproducible. Una fila moderna G7.1, una respuesta legacy `NULL`, escalar o con forma ajena se rechaza antes de mutar inspecciones con 409 `clave-idempotencia-incompatible`. P5 no interpreta `respuesta_bytes` ni convierte bytes genéricos a `InspeccionDTO`; el núcleo tampoco decodifica ni reconstruye la respuesta JSONB P5. Reutilizar entre integraciones la misma clave de usuario no transforma una representación en la otra ni reemplaza el resultado original.

Esto es una barrera de compatibilidad, no la unificación G7.2. No corrige el replay P5 anterior a comprobar la titularidad del recurso solicitado (aunque el controller exige autenticación/rol), el vínculo actual de claves entre endpoints/recursos, la coordinación de solicitudes P5 concurrentes ni la reproducción byte-exacta P5 (que sigue basada en JSONB). Esas limitaciones requieren una decisión y trabajo aparte; tampoco se introducen canonicalización, esperas predeterminadas ni allowlists en este arreglo.

Evidencia de compatibilidad (26/10/2026 UTC): el merge humano `aeb5e64` falló al compilar por visibilidad del repository (6 errores). Tras reparar visibilidad, las regresiones HTTP reprodujeron 8 respuestas 500 donde correspondía 409; con la barrera pasan 8/8 y las focalizadas de núcleo/borrador/envío/compatibilidad pasan 41/41. Suite global 1484/1484, sin omisiones; gates JaCoCo, formato/package/PMD y diccionario sin cambios aprobados. El primer check global falló por formato en tres archivos del arreglo, luego corregidos. Revisión independiente de código sin defecto causado por el candidato; no se ejecutó una corrida independiente adicional. No hay comparación HTTP diferencial con el merge base porque este no compila. La revisión nativa de este nuevo candidato no se considera cubierta por el cierre histórico G7.1.

## Límites externos

El callback y la fila del resultado comparten transacción PostgreSQL, no una transacción con MinIO ni otro sistema. Ante resultado externo incierto, el integrador debe reconciliar antes de repetir efectos; este ADR no afirma una garantía de recuperación operativa. No se implementan rutas, DTO, filtros ni handlers por defecto.

## Cierre G7.1

Cerrado en su alcance común por decisión explícita de la persona usuaria: «Close G7.1», después de informar la evaluación nativa indisponible y ofrecer cierre con la prueba disponible sin revisión nativa. No se atribuye aprobación nativa ni consumo de autoridad, ni se cambia RDD para otros candidatos. Los handlers y sus parámetros continúan pendientes en G7.2.

## Verificación

`IdempotenciaServiceTest` usa `PruebaIntegracion` (PostgreSQL 17/PostGIS y MinIO Testcontainers) para replay de bytes arbitrarios/vacíos, huella y versión incompatibles, aislamiento por usuario, concurrencia, timeout no cacheado, rollback/reintento, rechazo de filas heredadas con JSONB escalar/nulo, validación, copias defensivas y rechazo de transacción ambiente. Estas pruebas verifican el núcleo común; no habilitan ni aceptan handlers HTTP.

Evidencia local del 26/10/2026 (UTC): pruebas focalizadas 9/9 y `./mvnw -B -ntp clean verify` 1233/1233, sin omisiones y con los tres gates JaCoCo aprobados. `spotless:check package pmd:check -DskipTests` aprobado tras corregir formato. Los intentos anteriores de suite y diccionario fallaron porque `target/classes` conservaba una entidad `common.Auditoria` eliminada del código actual; `clean` resolvió la colisión sin cambiar auditoría. La revisión independiente del código no encontró defectos; su ejecución separada fue indisponible, no se cuenta como otra corrida aprobada. No se ejecutó una actualización sobre datos históricos insertados antes de la migración ni una prueba específica de fallo al insertar el resultado; las pruebas de claves heredadas y rollback del callback no sustituyen esas evidencias.
