# Idempotencia al guardar un borrador de inspección

Este contrato corresponde únicamente a `PUT /api/v1/inspecciones/{id}`. No habilita ni cambia otras escrituras. El POST de envío y las rutas para iniciar, eliminar o modificar otros datos conservan su contrato actual.

## Solicitud

Se requiere un JWT vigente de una cuenta activa con rol `ESTUDIANTE`, `PROFESIONAL_EXTERNO` o `CATEDRATICO`, titular de la inspección. El cuerpo sigue siendo `GuardarAvanceDTO`; `datos` y `version` son obligatorios.

| Encabezado | Uso |
|---|---|
| `Idempotency-Key` | Opcional. Si falta o contiene solo espacios se conserva el flujo anterior sin reserva idempotente. Un valor no blanco se recorta con `strip()` y admite hasta 100 puntos de código Unicode. Más de 100 devuelve 400 `idempotencia-invalida`. |
| `X-Sgp-Sync` | Opcional, predeterminado `false`. Con `true`, permite fusionar un avance basado en una versión desactualizada y guardar copia de conflicto. Su valor booleano forma parte de la huella. |

La clave se reserva en el ámbito usuario autenticado + valor recortado. Reutilizarla para otra inspección, método, endpoint, modo sync o DTO no ejecuta la operación: devuelve 409 `clave-idempotencia-incompatible`. La identidad y la clave se excluyen de la huella; el usuario/clave son la identidad de la reserva.

## Huella v1

La huella es SHA-256 de la serialización canónica de `PUT`, la ruta con UUID canónico, `X-Sgp-Sync` como booleano y el DTO completo. Los objetos se ordenan recursivamente por nombre de propiedad, pero los arreglos conservan su orden. Los números se normalizan como `BigDecimal` sin ceros finales; todas las representaciones numéricas de cero se reducen a `BigDecimal.ZERO`. La huella almacenada lleva versión `1`.

Por ejemplo, un reintento después de perder la respuesta debe repetir la misma ruta, clave, `X-Sgp-Sync` y cuerpo lógico. El orden de propiedades JSON no cambia la huella; cambiar el orden de un arreglo o el valor de cualquier campo sí la cambia.

## Respuestas y almacenamiento

| Status | Resultado | ¿Se guarda y reproduce con la misma huella? |
|---|---|---|
| 200 | Avance guardado; el cuerpo es el DTO de inspección. | Sí, conserva status y bytes. |
| 400 | DTO/encabezado inválido o clave mayor de 100 caracteres (`idempotencia-invalida`). | No. |
| 401 / 403 | Sin sesión o rol no autorizado. | No. |
| 404 | `inspeccion-no-encontrada`: inexistente, eliminada o ajena/no visible. | No. |
| 409 | `version-desactualizada`, `inspeccion-no-editable`, `clave-idempotencia-incompatible` u `operacion-en-proceso`. | No. |
| 413 | `cuerpo-demasiado-grande`. | Sí; rechazo terminal por payload. |
| 422 | `campo-inmutable`, `grupo-con-danos`, `datos-invalidos` o `fecha-inspeccion-invalida`. | Sí; rechazo terminal por payload. |
| 500 | Fallo inesperado. | No. |

La respuesta HTTP reproduce status y bytes, no encabezados dinámicos. El 200 se entrega como `application/json`; un problema reproducido usa `application/problem+json`.

La propiedad/visibilidad se comprueba antes del replay para no revelar una respuesta guardada a otra persona ni después de eliminar la inspección. Para una ejecución nueva también se comprueban estado editable y versión. Un replay autorizado devuelve el resultado confirmado, aunque el borrador haya cambiado de estado después.

La espera por la misma clave dura como máximo 2 segundos. Si vence, se devuelve 409 `operacion-en-proceso`; no se registra ese conflicto y el cliente puede reintentar con la misma clave. Los efectos en PostgreSQL y la respuesta idempotente se guardan en una sola transacción. Fallos no admitidos por la allowlist revierten la transacción y no dejan reserva.

Las filas preexistentes que solo contienen respuesta JSONB no se convierten ni se vuelven a ejecutar: una clave que encuentre una fila heredada recibe 409 `clave-idempotencia-incompatible`. La compatibilidad JSONB del POST de envío se mantiene separada.

## Ejemplo de uso

Enviar el PUT autenticado con una clave estable para esa operación y conservar `X-Sgp-Sync` y el DTO al reintentar:

```http
PUT /api/v1/inspecciones/0199c200-0000-7000-8000-0000000000b4
Authorization: Bearer <token de acceso>
Content-Type: application/json
Idempotency-Key: inspeccion-0199c200-guardar-1
X-Sgp-Sync: false

{"datos":{"identificacion":{"tipologia":"losas"}},"version":4}
```

Swagger expone el contrato en `/api/docs/swagger-ui.html`. Para decisiones de huella, transacción, errores y compatibilidad, véase [ADR 0046](adr/0046-idempotencia-del-put-de-borrador.md) y el [núcleo común G7.1](adr/0041-nucleo-comun-de-idempotencia.md).
