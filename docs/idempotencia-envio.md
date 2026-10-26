# Reintentar un envío sin repetir sus efectos

`POST /api/v1/inspecciones/{id}/envio` permite recuperar la respuesta confirmada usando `Idempotency-Key`. Requiere sesión activa, rol ESTUDIANTE, PROFESIONAL_EXTERNO o CATEDRATICO y una inspección propia no eliminada, incluso para reproducir resultados.

## Uso rápido

1. Genere una clave nueva por solicitud de envío y consérvela con el cuerpo y modo sync.
2. Envíe POST con `Idempotency-Key: envio-<identificador-unico>`, `Content-Type: application/json` y, por ejemplo, `{"enviada_en":"2026-10-25T16:05:00Z"}`. `X-Sgp-Sync` es opcional y predeterminado `false`.
3. Si no recibió respuesta o recibe 409 `operacion-en-proceso`, reintente con la misma ruta, clave, DTO y modo sync. El servidor espera como máximo 2 segundos por la clave concurrente. El conflicto no se cachea.
4. Si recibe 422 `inspeccion-no-publicable`, corrija los datos/permanentes/fotos indicados y use una clave nueva para volver a evaluar la inspección.

## Contrato

| Caso | Comportamiento |
|---|---|
| Sin clave o clave en blanco | Flujo anterior, sin guardar resultado idempotente |
| Clave no blanca | Se recorta; máximo 100 caracteres Unicode. Exceso: 400 `idempotencia-invalida` |
| Misma clave y huella | Status y cuerpo byte-exactos del 200 ENVIADA/PUBLICADA o del 422 guardado |
| Otro recurso, payload o X-Sgp-Sync | 409 `clave-idempotencia-incompatible`; no ejecuta efectos |
| Clave antigua con solo JSONB | Mismo 409; no convierte ni repite el envío |
| Cuenta/rol sin permiso, recurso ajeno/eliminado | Error normal antes de cualquier replay |
| Estado no editable o error HTTP de binding | No guarda resultado |
| Fallo transitorio/de persistencia | Revierte efectos y resultado; permite reintentar |

La huella SHA-256 v1 contiene POST, ruta con UUID canónico, booleano sync y DTO completo canonicalizado. Orden de propiedades y escala numérica no cambian la identidad. `indice_condicion` y `estado_calculado` del cliente integran la huella aunque se ignoren al calcular el resultado oficial. Cuerpo ausente y `{}` son solicitudes distintas; mantenga esa distinción al reintentar. Las fechas asignadas por el servidor quedan fuera de la huella.

La clave está asociada al usuario y comparte espacio con PUT y el núcleo común: no reutilice una clave para otra operación. Un replay no reevalúa editabilidad; sí vuelve a autorizar propiedad y existencia. Una ENVIADA sin clave conserva su retorno 200 sin duplicar transición. Una PUBLICADA admite replay de su clave confirmada, no una nueva mutación.

## Garantías y límites

El resultado y los efectos PostgreSQL —transiciones, publicación, auditoría y avisos persistidos— comparten transacción. No se garantiza entrega física exactamente una vez de correo, eventos externos o paquetes de red. Los oyentes pueden haber sido invocados antes de un rollback; solo sus escrituras en esa transacción se revierten. Los encabezados dinámicos no forman parte del replay.

No cambian la retención, PUT ni otras escrituras. Detalles de diseño: [ADR 0047](adr/0047-idempotencia-del-post-de-envio.md); encabezados y esquemas: `/api/docs/swagger-ui.html`.
