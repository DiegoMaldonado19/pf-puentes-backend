# 0049 — Replay de permanentes y acompañantes sin repetir efectos

- Estado: implementado y verificado localmente para M03/M04; GREEN, gates, diferencial y verificación independiente aprobados. Sin aceptación integral de G7.2.
- Fecha: 27/10/2026.
- Trazabilidad: G7.2 M03/M04, S1–S3, RN-OFF-08 y DT-OFF-07.
- Alcance: POST de confirmación de permanentes y PUT de acompañantes, bajo `/api/v1/inspecciones/{id}`.

## Decisión

Ambas rutas reutilizan [G7.1](0041-nucleo-comun-de-idempotencia.md), con **huellas independientes y resultados admitidos solo de éxito: 200 `application/json`**. Se guardan status y bytes originales, no se reconstruye el detalle actual. Un retry antiguo no vuelve a confirmar permanentes ni restaura una lista anterior.

S2 delega el diseño técnico, sin cambiar políticas de negocio. No se modifican permisos, validación, merge de permanentes, reemplazo de acompañantes, auditoría, máquina de estados ni retención. No se amplían las huellas o allowlists de [PUT](0046-idempotencia-del-put-de-borrador.md), [envío](0047-idempotencia-del-post-de-envio.md) o [alta](0048-idempotencia-del-alta-de-inspeccion.md).

## Contratos por ruta

| Parámetro | M03: permanentes | M04: acompañantes |
|---|---|---|
| Método y recurso | `POST /api/v1/inspecciones/{UUID canónico}/confirmacion-datos-permanentes` | `PUT /api/v1/inspecciones/{UUID canónico}/acompanantes` |
| Huella | SHA-256 versión 1 del objeto `{method,path,dto}`; DTO completo `ConfirmarDatosPermanentesDTO` | SHA-256 versión 1 del objeto `{method,path,dto}`; DTO completo `ReemplazarAcompanantesDTO` |
| Contenido | `datos`, incluidos campos desconocidos para el formulario o ignorados por el merge; objetos ordenados recursivamente, números decimales sin ceros finales/cero normalizado, arrays en su orden | `acompanantes` en su orden, con `nombre` original sin recortar y `usuario_id` canónico o null; propiedades de objetos ordenadas |
| Distinciones | Null y objeto vacío siguen siendo distintos. No se eliminan valores antes de calcular la huella | Lista vacía, orden, nombre y UUID distintos no comparten resultado. No se recorta el nombre para el hash aunque el dominio lo recorte al escribir |
| Resultado admitido | Solo 200 `application/json`, bytes del primer detalle confirmado | Solo 200 `application/json`, bytes del primer detalle con la lista reemplazada |
| Efectos que no se repiten | Merge, fecha de confirmación, origen de última publicación, actividad y auditoría | Borrado/recreación de relaciones, actividad y auditoría |

Los parámetros comunes se eligen **explícitamente para ambas rutas**, no por herencia de PUT/envío:

- Clave opcional; sin clave o con blanco se conserva el camino anterior. Clave no blanca recortada mediante `strip()`, máximo 100 puntos de código Unicode. Exceso: 400 `idempotencia-invalida` sin efectos.
- Espacio de claves: usuario autenticado + clave, compartido con los demás consumidores G7.1. Reutilizar una clave para otra ruta/recurso/contenido da 409 `clave-idempotencia-incompatible`.
- Espera máxima del bloqueo por usuario/clave: **2 segundos**. Agotamiento: 409 `operacion-en-proceso`, no persistido y reintentable con la misma clave. No es un timeout global del recurso ni de la petición.
- Resultado heredado JSONB sin huella/bytes verificables, otra versión o huella: 409 `clave-idempotencia-incompatible`; no convertir, sobrescribir ni ejecutar.
- Ningún rechazo es cacheable. Binding, validación de datos/nombres/autor, permisos, inexistencia, estado, colisión optimista y fallos transitorios se propagan; no reservan clave. La allowlist de resultados admitidos es únicamente `{200}`.
- Sin expiración automática ni purga nueva de resultados. Las bajas siguen su política existente.
- Se canonicaliza el DTO deserializado. Propiedades HTTP desconocidas fuera del DTO no participan. En M03 las propiedades dentro de `datos` sí participan. No se incluyen identidad autenticada, clave, timestamps generados por el servidor ni `X-Sgp-Sync`, que no tiene semántica en estas rutas.

## Autorización y replay tardío

La seguridad existente verifica autenticación y rol inspector. Antes de consultar el resultado se usa `InspeccionService.propio`: debe existir una inspección propia no eliminada. Se vuelve a comprobar al terminar la ejecución/espera, antes de entregar los bytes, para cubrir pérdida de titularidad o baja durante la espera. Conocer la clave no concede acceso.

Solo las operaciones nuevas ejecutan `propioEnBorrador`. Un replay confirmado sigue funcionando después de una edición, otra confirmación/lista o publicación. No exige nueva editabilidad ni calcula otra procedencia. Si el recurso pasa a ser ajeno o eliminado, se conserva el 404 existente y el resultado no se revela; no se borra la evidencia idempotente. No se añade una exigencia de asignación que estas escrituras no tenían.

## Frontera transaccional y carreras

Las entradas idempotentes **no abren una transacción ambiente**. `IdempotenciaService.ejecutar` abre la transacción PostgreSQL READ_COMMITTED, serializa por usuario/clave y llama la misma implementación de dominio del camino histórico. La llamada interna a `confirmar` o `reemplazarAcompanantes` no necesita otro proxy transaccional: ya participa en la transacción del núcleo. El camino sin clave sigue entrando por su proxy `@Transactional` anterior.

Se reutilizan sin duplicar los pasos de validación/merge de `PrecargaInspeccionService`, validación/reemplazo de `CapturaBorradorService` y la entrada externa `EscrituraBorradorInspeccion.guardar`, transaccional y `@Auditable`. No se escribe directamente en auditoría. La serialización del DTO y el guardado/commit del resultado quedan dentro de la misma transacción. Un fallo real al guardar el resultado revierte datos, lista completa —incluidos ids de relaciones anteriores—, procedencia, confirmación, actividad, aviso, versión, auditoría y reserva.

**Por qué solo éxito:** no se capturan errores para convertirlos en resultados admitidos. Así se evita confirmar efectos parciales por admitir un rechazo y no hace falta introducir una segunda implementación de validación. Los rechazos pueden volver a evaluarse con la misma clave; una corrección que sigue a un éxito necesita otra clave. PUT/envío conservan sus rechazos terminales propios.

El advisory lock no es un bloqueo del recurso. Claves distintas conservan `@Version` de `Inspeccion` y la transacción del reemplazo de relaciones: si ambas leen una versión, solo una confirma; la perdedora revierte también lista/auditoría/resultado. Se conserva el tratamiento HTTP actual de la excepción optimista: **500 no cacheable**, sin convertirla en éxito ni rediseñar el manejador común. Una petición posterior puede reintentar la clave que no quedó confirmada. Si lee después del commit ajeno, puede aplicar normalmente sobre la nueva versión; no se promete orden de llegada entre claves. Una transición concurrente que incrementa versión tampoco permite confirmar una escritura leída como BORRADOR sobre el estado cerrado.

No hay efectos MinIO en estas rutas ni reconciliación externa que introducir. No se afirma entrega física exactamente una vez ni cobertura del transporte frontend.

## Verificación y límites

Las pruebas `InspeccionPermanentesIdempotenciaTest` y `InspeccionAcompanantesIdempotenciaTest` usan HTTP MockMvc con `PruebaIntegracion`, PostgreSQL real y snapshots de dominio/relaciones/contadores. Incluyen replay exacto, retry tras ediciones/publicación, autorización, incompatibilidad entre rutas, legado, límite Unicode, no-key/blank, rechazos sin efectos, concurrencia de misma clave y espera. Un CHECK real sobre `idempotencia` exige rollback completo; no se simula atomicidad con mocks.

Las pruebas de carreras usan un bloqueo PostgreSQL de la fila para detener escritores HTTP reales y observar dos claves que leyeron la misma versión, o una publicación intermedia con cambio de versión. La sincronización consulta la cadena viva `pg_locks`/`pg_blocking_pids` desde la conexión bloqueadora, incluyendo esperas transitivas del reemplazo de relaciones; no depende de texto SQL ni de snapshots de actividad. No introducen bloqueos nuevos en producción. `InspeccionOpenApiTest` exige el esquema 200 `InspeccionDTO` aunque la implementación devuelva bytes.

### Evidencia observada

El runner padre comunicó **job6, exit 0, 192 segundos para el conjunto de checks**, con JDK 25. No es una ejecución independiente del writer ni se atribuye esa duración únicamente a la suite.

| Comando | Resultado comunicado por el padre |
|---|---|
| `./mvnw -B -ntp -Dtest=InspeccionPermanentesIdempotenciaTest,InspeccionAcompanantesIdempotenciaTest,InspeccionPrecargaTest,InspeccionBorradorTest,InspeccionOpenApiTest test` | 68/68; cero fallos, errores u omisiones |
| `./mvnw -B -ntp verify` | 1684/1684; cero fallos, errores u omisiones; los tres gates JaCoCo cumplidos |
| `./mvnw -B -ntp spotless:apply` | Exit 0; candidato formateado |
| `./mvnw -B -ntp spotless:check package pmd:check -DskipTests` | Exit 0 |
| `git diff --exit-code -- docs/diccionario-de-datos.md` | Exit 0, sin cambios |
| `git diff --check` | Exit 0 |

Los informes locales de pruebas están bajo `target/surefire-reports/`. La auto-revisión posterior al formato no detectó un defecto concreto que exigiera cambiar fuentes. El diferencial HTTP de 24 casos contra f57e0ed aprobó (job7, exit 0): status, Content-Type y cuerpo idénticos, con normalización exclusiva de `datos_permanentes_confirmados_en` y `sincronizada_en`. Se conservan stdout/stderr, sin declarar igualdad de logs Maven. El verificador independiente mvq8wva1-d-z9bt ejecutó 68/68 en copia aislada, sin bloqueantes reproducidos; constató métodos históricos idénticos. La pérdida de titularidad durante la espera tiene evidencia estructural, sin sonda temporal dedicada. Esto verifica T2/M03–M04, no acredita aceptación integral de G7.2.

### Secuencia test-first y correcciones de sondas

El RED previo a producción fue 64 pruebas, 24 fallos funcionales y cero errores/omisiones. Los intentos GREEN intermedios del padre tuvieron 68 pruebas con 14 fallos y después un fallo, sin errores/omisiones. Se corrigieron únicamente sondas: orden de miembros de errores no cacheables, observación de bloqueos y comparación GET/ACK.

El GET puede redondear la confirmación de nanosegundos en memoria a microsegundos PostgreSQL; esa conversión se aplica solo a la comparación de lectura persistida, **nunca al replay**. La carrera mide el incremento de versión frente a la ruta sin clave sobre otro borrador: permanentes conserva sus dos flushes históricos alrededor de la consulta de origen. Los replays siguen comparándose byte a byte sin normalización y el CHECK real sigue exigiendo rollback de dominio, relaciones y contadores.

No se prueba pérdida física de respuesta ni revocación JWT real: los retries son posteriores al commit y las identidades son las de prueba. M05/M06/M08/M09, G9/G10 y V06/V13 integrales quedan fuera de T2.

Guía de uso: [idempotencia del contexto de captura](../idempotencia-contexto-captura.md).
