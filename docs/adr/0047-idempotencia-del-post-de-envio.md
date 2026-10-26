# 0047 — Idempotencia HTTP del POST de envío de inspección

- Estado: implementado
- Fecha: 26/10/2026
- Trazabilidad: G7.2 POST, S1–S8; extiende el [núcleo G7.1](0041-nucleo-comun-de-idempotencia.md).

## Contexto y alcance

El POST de envío conservaba respuestas JSONB sin huella, consultadas antes de autorizar titularidad. Esto no permite garantizar replay byte-exacto ni distinguir una solicitud diferente. Se integra exclusivamente `POST /api/v1/inspecciones/{id}/envio`; no cambian PUT, retención, migraciones ni otras operaciones.

## Decisión

Se reutiliza `IdempotenciaService`, con una espera explícita de 2 segundos y su exclusión por usuario/clave en PostgreSQL. El controller separa la ruta con clave de la ruta transaccional anterior sin clave. La entrada idempotente no inicia una transacción ambiente: el núcleo abre la transacción que comprende callback y resultado.

Antes del núcleo se exige inspección propia y no eliminada. El callback repite esa autorización y exige editabilidad solo para una ejecución nueva que pueda mutar. Se conserva el retorno 200 de una inspección ya ENVIADA sin nueva transición. Un replay autorizado de PUBLICADA devuelve el resultado guardado; una ejecución nueva sobre PUBLICADA conserva el rechazo de estado anterior.

### Identidad y resultados

`HuellaEnvioIdempotencia` calcula SHA-256 v1 sobre método POST, ruta con UUID canónico, booleano `X-Sgp-Sync` y DTO completo canonicalizado. Ordena propiedades y normaliza escala/cero de los números. Incluye incluso IC y estado calculado enviados por el cliente, aunque el dominio los ignore. Cuerpo ausente (`null`) y `{}` son distintos. Las fechas generadas por el servidor no participan; `enviada_en` aportada por el cliente sí.

| Resultado | Persistencia |
|---|---|
| 200 ENVIADA o PUBLICADA | Status y bytes exactos; `application/json` |
| 422 `inspeccion-no-publicable` | Status y bytes exactos; `application/problem+json` |
| 409 `operacion-en-proceso` tras 2 segundos | No se guarda; reintentar con la misma clave |
| Legado JSONB, otra huella o versión incompatible | 409 `clave-idempotencia-incompatible`, sin ejecutar ni convertir |
| Autenticación, permisos, inexistencia, estado, binding, fallos transitorios | No se guarda resultado |

El 422 se captura únicamente durante la validación previa a las mutaciones. Corregir datos, confirmar permanentes o agregar fotos no invalida el resultado: se requiere una clave nueva. No se convierten en respuestas cacheables errores de publicación o de persistencia.

### Atomicidad y límites

Transición, sincronización, publicación, efectos PostgreSQL de los oyentes, avisos persistidos, auditoría y resultado se confirman o revierten juntos. Un fallo al guardar resultado se propaga y revierte el callback; no se silencia una violación de integridad. Un replay no publica eventos ni vuelve a crear avisos.

La garantía cubre efectos persistidos en la misma transacción, no entrega física exactamente una vez de correo, red ni efectos externos de oyentes. Un oyente síncrono puede haber sido invocado aunque después haya rollback; un registro en memoria no se revierte. No se introduce outbox ni protocolo nuevo de entrega. Tampoco se reproducen encabezados dinámicos como Date o seguridad.

## Compatibilidad y consecuencias

Sin `Idempotency-Key` o con valor en blanco se conserva la ruta anterior. Una clave no blanca se recorta con `strip()` y admite hasta 100 puntos de código Unicode; mayor longitud responde 400 `idempotencia-invalida`. Las claves comparten espacio por usuario con PUT y otras operaciones del núcleo: no deben reutilizarse para solicitudes distintas.

No hay cambios de esquema ni retención. El reintento posterior al commit recupera status/cuerpo sin repetir efectos PostgreSQL; las pruebas simulan ese reintento, no una pérdida física de respuesta en la red.

## Verificación

`InspeccionEnvioTest` cubre replay ENVIADA/PUBLICADA, huella, autorización, 422 persistido tras corregir, exclusiones, concurrencia, timeout y rollback forzado del resultado. `IdempotenciaCompatibilidadTest` incluye legado JSONB válido e inválido y colisiones con el núcleo. `InspeccionOpenApiTest` preserva el esquema 200 y documenta ProblemDetail. La [guía de uso](../idempotencia-envio.md) describe cómo reintentar.
