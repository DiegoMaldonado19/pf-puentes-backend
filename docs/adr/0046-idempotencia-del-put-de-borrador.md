# 0046 — Idempotencia HTTP del PUT de borrador de inspección

- Estado: implementado
- Fecha: 26/10/2026
- Trazabilidad: G7.2/T1, S1–S7 del plan P6; extiende el núcleo común documentado en [ADR 0041](0041-nucleo-comun-de-idempotencia.md).

## Contexto y alcance

G7.1 proporcionó `IdempotenciaService`, que serializa por usuario/clave y guarda status, huella y bytes de respuesta junto con el callback en una transacción PostgreSQL. El `PUT /api/v1/inspecciones/{id}` aún usaba el replay JSONB heredado, anterior a comprobar titularidad. G7.2 debe integrar el núcleo sin habilitar ni cambiar otras escrituras, en particular el envío `POST /api/v1/inspecciones/{id}/envio`.

Este ADR resuelve exclusivamente la idempotencia opcional del PUT de avance. El PUT sin clave conserva su ruta previa; el POST de envío y las otras operaciones siguen su comportamiento anterior.

## Decisión

### Encabezados y clave

- `Idempotency-Key` es opcional. Si falta o contiene solo espacios, el controller ejecuta la ruta no idempotente ya existente. Una clave no blanca se recorta con `String.strip()`; se aceptan hasta 100 puntos de código Unicode. Una clave mayor devuelve 400 `idempotencia-invalida` sin consultar ni crear reserva.
- El núcleo identifica una reserva por usuario autenticado y clave recortada. La identidad y la clave no forman parte del digest de la huella.
- `X-Sgp-Sync` conserva su valor predeterminado `false` y se representa como booleano dentro de la huella. Cuando es `true`, se mantiene la fusión de datos y la copia de conflicto que ya usa la sincronización.

### Huella canónica v1

`HuellaBorradorIdempotencia` calcula SHA-256 sobre la serialización JSON canónica de estos cuatro valores: método `PUT`, ruta `/api/v1/inspecciones/<UUID canónico>`, booleano `X-Sgp-Sync` y DTO completo `GuardarAvanceDTO`. El UUID viene del `UUID` ya parseado por Spring y se serializa con su forma canónica. Las propiedades de todos los objetos se ordenan recursivamente; el orden de cada arreglo se preserva. Los números se convierten a `BigDecimal`, se eliminan ceros finales y cualquier cero se representa como `BigDecimal.ZERO`, de modo que variantes como `1`, `1.0` y `1e0` convergen. Se guarda la versión de huella `1`. Un reintento con la misma clave pero otra ruta, modo sync o DTO no puede ejecutar una segunda operación.

### Autorización, ejecución y replay

El filtro de seguridad y la autorización de rol se ejecutan antes del handler. Antes de consultar una reserva, el servicio vuelve a comprobar que la inspección existe, no está eliminada y pertenece al usuario; si no, devuelve el error normal sin reproducir datos. El callback de una clave nueva vuelve a aplicar la validación de borrador editable y versión. Un replay autorizado no vuelve a evaluar la editabilidad actual: devuelve el resultado previamente confirmado.

La espera máxima por la clave es de 2 segundos. Si se agota, la operación responde 409 `operacion-en-proceso`, no guarda una reserva ni la respuesta, y puede reintentarse con la misma clave.

El callback y la fila idempotente comparten la transacción del núcleo. La copia de conflicto, los cambios del borrador y status/bytes se confirman o revierten juntos. La reserva solo se inserta cuando el callback devuelve una respuesta admitida.

### Resultados reproducibles

Se guardan status y bytes para:

- 200: avance confirmado.
- 413 `cuerpo-demasiado-grande`.
- 422 `campo-inmutable`, `grupo-con-danos`, `datos-invalidos` y `fecha-inspeccion-invalida`.

Estos rechazos terminales por payload son resultados de esa solicitud: un reintento idéntico recibe la misma respuesta aunque cambie el estado del borrador. Los errores de autenticación, rol, titularidad/existencia, versión desactualizada, borrador no editable, clave/huella incompatible, timeout y fallos inesperados no se guardan. Los errores de binding/validación HTTP que ocurren antes de entrar al handler tampoco se guardan.

El controller devuelve `application/json` para 200 y `application/problem+json` para los rechazos reproducidos, conservando el status y bytes almacenados. No se promete repetir encabezados HTTP dinámicos como `Date` o encabezados de seguridad.

## Compatibilidad

Las filas antiguas de `idempotencia` que contienen respuesta JSONB pero no versión, digest ni bytes no se reinterpretan ni se ejecutan de nuevo. El nuevo PUT devuelve 409 `clave-idempotencia-incompatible`. Se preserva la lectura JSONB que necesita el envío P5; no se amplía aquí la garantía HTTP del POST.

La nueva huella contiene ruta, por lo que reutilizar usuario/clave desde otro recurso o endpoint no crea una segunda reserva: produce incompatibilidad. La unicidad compartida de usuario/clave permanece a cargo del núcleo común.

## Consecuencias

- La pérdida de respuesta tras un commit se recupera repitiendo método, ruta, `Idempotency-Key`, `X-Sgp-Sync` y DTO; se obtiene el status y cuerpo exactos sin repetir efectos PostgreSQL.
- Los errores no terminales siguen siendo observables en cada solicitud y no bloquean un reintento tras corregir la condición.
- No se añade migración, tabla, política de expiración ni mecanismo externo. No se habilita idempotencia nueva en POST de envío ni en otras escrituras.
- El contrato HTTP y los ejemplos de integración están en [idempotencia-borrador.md](../idempotencia-borrador.md); el controller documenta encabezados, respuestas y errores en OpenAPI.

## Verificación prevista

`InspeccionBorradorTest` usa `PruebaIntegracion` con PostgreSQL/Testcontainers para verificar el contrato HTTP, replay byte-exacto, cambios de payload/ruta/modo, rechazo de claves JSONB heredadas, autorización previa al replay, compatibilidad sin clave, validación terminal y rollback, sincronización, concurrencia y timeout. `InspeccionEnvioTest` conserva la verificación de compatibilidad de POST de envío; `IdempotenciaCompatibilidadTest` cubre las representaciones heredadas del núcleo.
