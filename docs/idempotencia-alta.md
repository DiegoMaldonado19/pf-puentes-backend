# Reintentar un alta sin repetir sus efectos

`POST /api/v1/inspecciones` acepta `Idempotency-Key` para altas y correcciones. Un reintento autorizado con la misma clave y DTO devuelve **201 y el cuerpo original byte por byte**, aunque después se haya editado o publicado la inspección. No crea otra entidad, transición ni auditoría.

## Camino rápido

1. Genere el UUID v7 de la inspección antes del primer envío.
2. Asigne una clave estable a la operación; conserve el DTO completo enviado.
3. Reintente con el mismo usuario, clave y DTO si pierde la respuesta o recibe un fallo transitorio.
4. Confirme el ACK de esa operación, no una edición posterior. Para consultar estado actual use `GET /api/v1/inspecciones/{id}`.

```http
POST /api/v1/inspecciones
Content-Type: application/json
Idempotency-Key: alta-tablet-42

{"id":"0199c200-0000-7000-8000-000000000c01","puente_id":"0199c200-0000-7000-8000-000000000001","fecha_inspeccion":"2026-10-26","dispositivo_id":"tablet-42"}
```

Los identificadores y fecha del ejemplo requieren recursos, acceso y fecha válidos en el servidor de destino. La clave se recorta y admite 100 caracteres Unicode. Sin clave o con valor en blanco sigue el comportamiento anterior: UUID propio existente devuelve detalle actual; UUID ajeno o eliminado produce conflicto. Ese camino no reserva ni fija una respuesta original.

## Qué debe permanecer estable

La huella SHA-256 v1 cubre POST, `/api/v1/inspecciones` y **todo CrearInspeccionDTO**:

- UUID, puente, asignación y curso.
- Fecha de inspección, dispositivo y creación en dispositivo.
- Latitud y longitud.
- Inspección corregida y motivo de corrección.

Incluye campos que el dominio ignore. Orden de propiedades, mayúsculas del UUID y escalas numéricas equivalentes no cambian la huella. Strings distintos sí la cambian, incluso espacios que el dominio recorte al escribir. Ausencia y null siguen el DTO deserializado. Autenticación, clave y horas generadas por servidor quedan fuera. `X-Sgp-Sync` no aplica al POST de alta.

No reutilice la clave en PUT, envío u otro recurso: el espacio es por usuario, no por ruta. Corregir explícitamente el payload requiere una operación/clave nueva; no cambie automáticamente la clave para eludir un rechazo.

## Resultados y acción

| Respuesta | Qué significa | Acción |
|---|---|---|
| 201 | Alta nueva, UUID propio deduplicado o replay exacto. Único resultado persistido por M01. | Confirmar la operación enviada. |
| 409 `operacion-en-proceso` | No adquirió el bloqueo por usuario/clave en 2 segundos. No se guarda este conflicto. | Reintentar la misma clave y DTO. |
| 409 `clave-idempotencia-incompatible` | Otra huella/versión o clave JSONB heredada sin bytes verificables. No hubo mutación ni conversión. | Revisar identidad de operación; no sobrescribir la clave. |
| 409 `inspeccion-en-conflicto` | UUID ajeno o eliminado, incluso después de un alta confirmada. | Conservar captura y resolver el conflicto; no resucitar ni forzar replay. |
| 400/401/403/404/409/422 restantes | Binding/UUID, autorización vigente o regla de negocio. No se cachean. | Mostrar causa y conservar datos locales; no forzar permisos/estado. |
| 500 | Falló la operación o su resultado; no se confirma el alta a medias. | Reintentar misma clave/DTO cuando se resuelva el fallo. |

La espera de 2 segundos limita el bloqueo idempotente, no toda la petición. No hay expiración automática de resultados/huellas. No se reproducen encabezados dinámicos como Date, solo status y cuerpo originales.

## Corrección, autorización y eliminación

Una corrección usa el mismo POST con `supersede_a_id` y `motivo_correccion`; se mantienen las reglas de publicación de origen, mismo puente y una corrección viva. La primera ejecución aplica todas las validaciones de alta. Un replay confirmado no exige otra vez formulario activo, editabilidad o ausencia de corrección viva.

Antes de revelar un replay se exige rol inspector, autor actual, identidad no eliminada y acceso vigente; el estudiante debe conservar asignación vigente mediante los servicios existentes. Una clave no concede acceso. La autorización se revalida también después de la espera y antes de responder.

Si elimina el borrador/corrección y luego inicia otra corrección, reintentar el alta eliminada responde 409 sin tocar la nueva. Se conserva el resultado para su retención, pero no se concede replay sobre el recurso eliminado. El contrato de replay de DELETE corresponde a M08 y no se incorpora aquí.

La baja es lógica y conserva la fila autorizable. No se añade purga ni tombstone: si faltara físicamente esa identidad, un resultado guardado no permite revelar datos ni recrear la inspección; la comprobación final deniega con 404. Cualquier futura política de eliminación física debe resolver esa evidencia antes de habilitar replay tras ausencia.

## Garantía y límites

Alta, transición inicial, auditoría y resultado se confirman o revierten juntos en PostgreSQL. El camino sin clave mantiene su transacción propia para preservar colisiones UUID concurrentes. Un UUID previo sin clave puede fijar su primer resultado con una clave nueva, sin duplicar escritura; ese resultado es el detalle del momento de adopción, no una reconstrucción del alta histórica perdida.

Pruebas de integración verifican retry posterior al commit, comparación de bytes, concurrencia y rollback mediante fallo real PostgreSQL; no equivalen a una pérdida física de respuesta ni a revocación JWT real. Esta entrega no habilita transporte frontend, formulario preparado G9.2 ni sincronización de archivos.

## Evidencia local de T1

- RED corregido antes de producción: 33 pruebas, 13 fallos funcionales, cero errores de fixture y cero omisiones.
- GREEN focalizado: 35/35; suite `verify`: 1650/1650 y gates JaCoCo aprobados. Spotless, package y PMD aprobados; diccionario sin diferencia.
- Diferencial contra `4ff5034`, en copias temporales: 11 respuestas HTTP iguales en status, Content-Type y bytes, sustituyendo solo `creado_en` por un valor fijo. Incluye POST sin clave/blanco, deduplicación con otro payload, corrección, UUID inválido, puente inexistente, baja lógica y PUT/envío con clave nueva/huella distinta. Ambas sondas terminaron con exit 0.
- Los dos escenarios añadidos después de implementar (corrección eliminada seguida de otra y aislamiento/retiro del formulario) y la extensión del rollback a corrección tienen evidencia GREEN, no un RED independiente.

Esto es autoverificación local, no aceptación externa ni revisión independiente. Los logs/detalles del runner se entregan al orquestador; no se incorporan credenciales ni pruebas que lean historia Git al repositorio.

Contrato técnico: [ADR 0048](adr/0048-idempotencia-del-alta-de-inspeccion.md). PUT y envío conservan [sus](idempotencia-borrador.md) [contratos](idempotencia-envio.md) independientes.
