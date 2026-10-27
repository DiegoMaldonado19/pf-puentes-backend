# Reintentar permanentes y acompañantes sin repetir cambios

Las dos escrituras del contexto de captura aceptan `Idempotency-Key`. Un retry autorizado con la misma clave y contenido devuelve **el 200 y los bytes originales**, aunque después se hayan confirmado otros datos, reemplazado la lista o publicado la inspección. No restaura el estado antiguo.

## Uso

1. Crear una clave estable por operación; conservarla junto con el método, recurso y DTO original.
2. Enviar el contenido original al reintentar. Para editar después de un éxito, crear otra operación y clave.
3. Procesar el ACK original; consultar el detalle autorizado por separado si se necesita el estado actual.

Prefijo: `/api/v1/inspecciones/{id}`.

| Operación | Método y sufijo | Cuerpo de ejemplo |
|---|---|---|
| Confirmar permanentes | `POST /confirmacion-datos-permanentes` | `{"datos":{"identificacion":{"tipologia":"losas","numero_tramos":3}}}` |
| Reemplazar acompañantes | `PUT /acompanantes` | `{"acompanantes":[{"nombre":"Par de campo","usuario_id":null}]}` |

`Content-Type: application/json` e `Idempotency-Key: contexto-tablet-001` son encabezados de ejemplo, no una clave compartida por las dos operaciones. No se debe reutilizar una clave de alta, avance o envío.

## Contrato observable

| Situación | Respuesta y acción |
|---|---|
| Misma clave/huella del mismo usuario | 200 `application/json` con bytes originales; no repite merge, confirmación, lista, actividad ni auditoría |
| Otra operación, recurso o contenido con clave ya confirmada | 409 `clave-idempotencia-incompatible`; sin cambios. Usar una clave nueva para otra operación |
| Resultado heredado JSONB no verificable | Mismo 409; no se convierte ni se vuelve a ejecutar |
| Duplicado todavía en curso | Espera hasta **2 segundos por usuario/clave**; si no queda libre, 409 `operacion-en-proceso`, reintentable con la misma clave |
| Sin clave o clave blanca | Camino anterior: cada llamada confirma/reemplaza y actualiza actividad; no guarda resultado idempotente |
| Clave demasiado larga | 400 `idempotencia-invalida`; máximo 100 caracteres Unicode tras `strip()` |
| Recurso ajeno, eliminado o inexistente | 404 antes de entregar replay; conocer clave/UUID no concede acceso |
| Rol sin permiso o sin autenticación | 403/401 de la seguridad existente; no se guarda resultado |
| Operación nueva sobre estado no BORRADOR | 409 `inspeccion-no-editable`; un replay confirmado autorizado no exige BORRADOR |
| Datos inválidos o autor como acompañante | Rechazo anterior de la ruta, sin cambios ni resultado guardado; la misma clave aún no confirmada puede volver a evaluarse |
| Fallo al guardar el resultado o carrera optimista | 500 no guardado; revierte también efectos de dominio/auditoría. Reintentar conserva la clave |

Solo se admiten éxitos 200. No se guardan rechazos de payload, permisos, estado, binding ni fallos transitorios. No hay expiración automática de resultados. PUT de avance y POST de envío conservan sus propias allowlists, que sí admiten determinados rechazos; no trasladar sus reglas a estas rutas.

## Qué identifica el contenido

- **Permanentes:** huella SHA-256 v1 de método POST, ruta con UUID canónico y DTO completo. Orden de propiedades JSON y números equivalentes no cambian la huella; el orden de arrays sí. Campos dentro de `datos` participan aunque el merge no los persista. Null y objeto vacío no se equiparan.
- **Acompañantes:** huella SHA-256 v1 de método PUT, ruta con UUID canónico y lista completa en su orden. Participan nombre sin recortar y `usuario_id` canónico/null. Cambiar espacios del nombre es otra operación, aunque la escritura recorte el nombre.
- Se usa el DTO deserializado: las propiedades HTTP desconocidas fuera de él no participan. No se incluyen clave, credenciales ni fechas generadas por el servidor. `X-Sgp-Sync` no tiene semántica aquí y no participa.

Cada usuario tiene su espacio de claves. La propiedad actual y el recurso no eliminado se verifican antes del núcleo y después de la espera. No se añade una política de asignación distinta de la escritura anterior.

## Atomicidad y límites

Resultado y dominio comparten una transacción PostgreSQL: permanentes/procedencia/confirmación o lista completa, actividad, aviso de abandono, versión y auditoría se confirman o revierten juntos. No hay efectos MinIO en estas dos operaciones.

La serialización de duplicados es **por clave**, no por recurso. Claves distintas mantienen el control optimista existente: dos escrituras que leyeron la misma versión no confirman ambas. La perdedora revierte todo y conserva el 500 actual del manejador común; no se guarda como rechazo terminal. El reintento de una operación aún no confirmada vuelve a validar el estado vigente.

El cliente todavía debe representar y transportar estas operaciones en G9/G10. Esta integración no acredita sincronización frontend integral, pérdida física de respuesta ni renovación/revocación JWT real.

## Verificación

Contrato técnico y alcance de pruebas: [ADR 0049](adr/0049-idempotencia-de-permanentes-y-acompanantes.md). Pruebas HTTP: `InspeccionPermanentesIdempotenciaTest`, `InspeccionAcompanantesIdempotenciaTest`; compatibilidad: `InspeccionPrecargaTest` e `InspeccionBorradorTest`; esquema documentado: `InspeccionOpenApiTest`.

El runner padre comunicó **job6, exit 0**, con 68/68 pruebas focalizadas y 1684/1684 en `verify`, sin fallos, errores ni omisiones. Los tres gates JaCoCo, formato/package/PMD, diccionario sin cambios y `git diff --check` también aprobaron. Los comandos y la procedencia de la evidencia están en el ADR.

**Verificado:** el diferencial HTTP contra f57e0ed (job7, exit 0) aprobó 24/24 casos de status, Content-Type y cuerpo, normalizando únicamente `datos_permanentes_confirmados_en` y `sincronizada_en`. Los stdout/stderr completos se conservaron, sin afirmar igualdad de logs Maven. El verificador independiente aprobó 68/68 sobre una copia aislada del candidato, sin bloqueantes reproducidos. Esto verifica M03/M04, no la sincronización integral.

La auto-revisión del candidato formateado conserva los métodos históricos del camino sin clave/blanco, sin añadirles validación de encabezado ni resultado persistido. Las pruebas ordinarias de compatibilidad y la comparación diferencial aprobaron; los replays se compararon byte a byte sin normalización.
