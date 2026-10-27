# 0048 — Replay exacto del alta y la corrección de inspecciones

- Estado: implementado; verificación de cierre registrada en la entrega T1.
- Fecha: 27/10/2026.
- Trazabilidad: G7.2 M01, S1–S3, RN-OFF-08 y DT-OFF-07.
- Alcance: únicamente `POST /api/v1/inspecciones`, incluida corrección.

## Decisión

El alta con `Idempotency-Key` reutiliza [G7.1](0041-nucleo-comun-de-idempotencia.md) para guardar y reproducir el **201 y sus bytes originales**. El UUID conserva su función de identidad de dominio; no sustituye la identidad de operación. No se cambian permisos, reglas de corrección, selección del formulario activo, retención ni otras rutas.

La persona usuaria delegó la definición técnica de los contratos manteniendo la política de negocio (S2). Esta decisión no atribuye aceptación externa a P1/P5 ni acredita sincronización frontend integral.

## Contrato de M01

| Parámetro | Decisión |
|---|---|
| Clave | Opcional; ausencia o blanco conserva el camino anterior. Una clave no blanca usa `strip()` y admite hasta 100 puntos de código Unicode. Exceso: 400 `idempotencia-invalida`, sin efectos. |
| Espacio | Usuario autenticado + clave, compartido con PUT/envío y los demás consumidores G7.1. No reutilizar una clave para otra operación. |
| Huella | SHA-256, versión 1, JSON de `method=POST`, `path=/api/v1/inspecciones` y `dto` completo. El recurso UUID y puente están dentro del DTO. |
| Canonicalización | DTO deserializado mediante el JsonMapper de la aplicación, propiedades ordenadas lexicográficamente; UUID en forma canónica; números decimales sin ceros finales y cero normalizado. Ausente/null sigue la representación del DTO; propiedades HTTP desconocidas no son parte del contrato del DTO. |
| Campos | `id`, `puente_id`, `asignacion_id`, `curso_id`, `fecha_inspeccion`, `dispositivo_id`, `latitud`, `longitud`, `creada_en_dispositivo`, `supersede_a_id`, `motivo_correccion`, incluso campos ignorados por la escritura de dominio. |
| Distinciones | No recortar strings del DTO para la huella: dispositivo/motivo distintos son operaciones distintas aunque el dominio recorte al persistir. Fechas del cliente sí participan; fechas generadas por servidor no. |
| Encabezados | Ni la clave ni autenticación participan en el hash. `X-Sgp-Sync` no tiene semántica en el alta y no se incorpora; no cambia sus contratos en PUT/envío. |
| Espera | 2 segundos para el bloqueo por usuario/clave; agotamiento: 409 `operacion-en-proceso`, reintentable y no persistido. No es un timeout global de la petición. |
| Resultado admitido | Solo 201 `application/json`, incluido el resultado de deduplicar un UUID propio creado previamente sin clave. Status y bytes exactos. |
| Errores | Ningún rechazo es terminal cacheable en M01. Binding/UUID, permisos, inexistencia, fecha/GPS, puente, formulario, corrección y fallos transitorios se propagan sin guardar resultado. |
| Legado/incompatibilidad | JSONB sin bytes/huella verificables, otra huella o versión: 409 `clave-idempotencia-incompatible`. No convertir, sobrescribir ni volver a ejecutar. |
| Retención | Sin expiración automática de resultados o huellas. No se modifica la política de baja ni se añade purga. |

## Autorización y baja posterior

Antes de entrar al núcleo se comprueba rol inspector. Si el UUID existe, se reutilizan las reglas del alta: mismo autor y no eliminado; en otro caso, 409 `inspeccion-en-conflicto`, sin revelar el resultado. Se comprueba el puente real del registro y la asignación vigente del estudiante mediante los servicios existentes. Para un UUID nuevo se comprueba acceso al puente solicitado. Autenticación/estado/rol remoto siguen siendo responsabilidad del filtro vigente; no se confía en conocer la clave.

El callback repite autorización dentro de la transacción. Antes de entregar cualquier respuesta guardada se revalida además que la fila aún exista, sea propia, no eliminada y permanezca autorizada; esto cubre cambios durante la espera. No exige nueva editabilidad, fecha válida hoy, formulario activo ni ausencia de corrección viva para un replay confirmado. Una publicación o edición posterior no obliga a reconstruir el DTO.

**Una baja lógica posterior impide el replay del alta, también si ya existe otra corrección viva.** Se conserva el 409 anterior: no se resucita el registro, no se devuelve su antiguo borrador y no se toca la corrección nueva. El resultado permanece guardado, pero no es una autorización. Esto no define el replay del DELETE (M08).

La identidad autorizable es la fila de inspección, conservada por la baja lógica existente. No se introduce tombstone ni borrado físico. Si esa identidad faltara físicamente mientras existe un resultado, el núcleo no ejecuta otra alta y la comprobación final devuelve 404, no los bytes guardados. Una futura purga tendría que conservar evidencia autorizable antes de habilitar replay tras ausencia; no queda autorizada por este ADR.

## Frontera transaccional

`InicioInspeccionService.iniciarIdempotente` no abre transacción ambiente. G7.1 abre una única transacción READ_COMMITTED: valida/reserva por usuario-clave, ejecuta el alta y guarda el resultado. La lógica de alta es común a ambos caminos y conserva las validaciones existentes.

`EscrituraAltaInspeccion.persistirParticipando` es una entrada externa auditada con propagación MANDATORY. Reutiliza la misma escritura privada que el alta histórica: entidad, transición inicial y flush. El aspecto de auditoría participa en esa misma transacción. Serialización, persistencia de resultado o commit fallidos revierten alta, transición, auditoría y resultado. No se capturan rechazos para guardarlos como éxitos.

El camino sin clave conserva `persistir` con REQUIRES_NEW y su recuperación histórica de colisión UUID; no se sustituye globalmente por REQUIRED. En el camino con clave una colisión PK se propaga fuera de la transacción abortada y se permite una única nueva evaluación mediante G7.1, que deduplica el UUID ya confirmado. La restricción de corrección viva conserva su conflicto de negocio. Claves distintas no garantizan bytes iguales entre sí, aunque compartan UUID; cada clave fija su propio primer resultado autorizado.

No hay efectos MinIO en M01. No se afirma atomicidad entre PostgreSQL y servicios externos ni entrega física exactamente una vez. PUT/envío mantienen sus propias huellas y allowlists.

## Verificación y consecuencias

`InspeccionAltaIdempotenciaTest` comprueba HTTP, bytes de replay tras edición/publicación, huella, autorización vigente, baja/corrección posterior, compatibilidad UUID/blanco, JSONB, concurrencia y espera. El fallo real de un CHECK PostgreSQL al insertar el resultado comprueba rollback de alta normal y corrección, transiciones, auditoría y contadores; no usa mocks de atomicidad. `InspeccionInicioTest` conserva las reglas históricas; `InspeccionOpenApiTest` fija el esquema 201 y el encabezado.

Las pruebas de retry tras commit no simulan corte físico de transporte. Identidades MockMvc no acreditan revocación JWT real. Los checks globales y la comparación diferencial deben acompañar el cierre T1; no se declaran aprobados solo por este documento.

Uso y tratamiento de errores: [guía de idempotencia del alta](../idempotencia-alta.md). Próximas unidades: M03/M04, archivos y eliminaciones, cada una con contrato propio.
