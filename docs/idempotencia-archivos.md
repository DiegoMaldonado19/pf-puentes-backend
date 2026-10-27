# Reintentar cargas PDF sin duplicar archivos

El corte M06 de T3 integra `POST /api/v1/inspecciones/{id}/documentos` con G7.1 e intención durable PostgreSQL–MinIO. **Verificado localmente: M06 tiene GREEN y verificación independiente; no acredita aceptación externa ni el cierre integral de T3.** M05 fotos y M09 DELETE idempotente siguen pendientes; no se habilitaron por compartir módulo.

## Camino rápido

1. Enviar multipart `id` y `pdf` a un borrador propio con `Idempotency-Key` estable.
2. Ante pérdida de respuesta o fallo transitorio, reenviar exactamente UUID, bytes y MIME con la misma clave.
3. Un 201 autorizado se reproduce con status/cuerpo originales. La URL puede estar vencida; el replay no renueva enlaces ni recrea un adjunto borrado posteriormente.

Sin clave o con clave en blanco continúa el contrato histórico de UUID/201. No existe aquí GET nuevo para enlaces frescos ni adaptador frontend.

## Contrato M06

| Tema | Regla |
| --- | --- |
| Identidad | Usuario y clave recortada con `strip`; máximo 100 puntos de código Unicode. |
| Huella v1 | SHA-256 de JSON con claves ordenadas: POST, ruta UUID canónica, UUID del archivo y manifiesto `pdf` con SHA-256/tamaño/MIME declarado, incluido null. |
| Exclusiones | Nombre cliente, orden/boundary multipart, autenticación y parámetros ajenos al DTO PDF. |
| Resultado admitido | Solo 201 JSON exacto; incluye enlaces originales aunque venzan. |
| Incompatibilidad | 409 `clave-idempotencia-incompatible` para otra huella o legado JSONB, sin sobrescribir ni ejecutar. |
| Espera | G7.1 espera hasta 2 s por clave; el callback limita cada bloqueo SQL a 2 s. 409 `operacion-en-proceso`, no cacheable. No es un deadline total HTTP. |
| Autorización | Padre propio/no eliminado y rol actual antes del replay; nueva comprobación tras el núcleo, incluido su camino de espera/replay. Mutación nueva revalida bajo bloqueo. |
| Editabilidad | Nueva fila solo en BORRADOR; replay no exige que siga editable ni que exista el adjunto. Padre eliminado o ajeno no revela el resultado. |
| Validación | PDF detectado por contenido, hasta 20 MiB y cupo de 10 documentos; conserva las reglas previas. |
| Rechazos | Binding, formato, cupo, estados, auth y transitorios no se guardan como resultados. Corregir una solicitud sin efectos no consume la clave. |

## Recuperación durable

`intento_documento` conserva usuario/clave/huella, UUID de archivo/padre, clave S3 elegida una vez, SHA-256 y tamaño esperado. No contiene blobs ni URLs firmadas. No tiene expiración automática, FK a filas efímeras ni proceso de purga nuevo.

G7.1 consulta el puerto genérico `IntencionIdempotencia` después de adquirir su bloqueo usuario/clave y antes de consultar/admitir resultado o ejecutar el callback. `CargaDocumentoService` aporta la huella durable sin que el núcleo conozca archivos, tablas ni DTO. Otra versión/huella recibe el mismo 409 `clave-idempotencia-incompatible`, incluso después de rollback sin resultado. Sin intención, el comportamiento previo y las allowlists por operación no cambian.

La intención se confirma en `REQUIRES_NEW` antes de MinIO, desde el callback que conserva el bloqueo usuario/clave exterior; la transacción nueva no lo adquiere otra vez. La consulta del puerto es de solo lectura y no bloquea recursos de negocio. `READ_COMMITTED` permite ver la intención confirmada por una ejecución anterior tras esperar su bloqueo, aunque esa ejecución revirtiera. El callback de G7.1 conserva además bloqueo de inspección→UUID durante la recuperación. La inspección remota lee el contenido completo por bloques: solo 404 de clave ausente equivale a ausencia; errores de red/permisos/lectura se propagan. Si falta el objeto, guarda y verifica; si coincide, reutiliza; si es incompatible, devuelve 409 `objeto-archivo-incompatible` sin sobrescribir.

La finalización de intención, fila de archivo, actividad y resultado comparten la transacción final. Si falla PostgreSQL, la intención queda pendiente y el retry puede reutilizar el objeto íntegro. Una clave distinta/sin clave para un UUID con intención pendiente recibe `operacion-en-proceso`; debe recuperarse la operación original. La reserva también protege frente a creación histórica de fotos, en el mismo padre, otro padre o una orden de mantenimiento. El creador común de foto/miniatura consulta la misma validación de reserva que PDF antes de escribir objetos, bajo el advisory lock UUID. Mantenimiento conserva su bloqueo de orden y luego adquiere ese mismo bloqueo UUID; no se añade idempotencia M05. Para UUID ya confirmado se conserva la deduplicación histórica, incluso entre tipos o cuando la nueva clave difiere.

Si se perdió autorización/editabilidad, no finalizar la carga pendiente ni inventar compensación. Se conserva la evidencia técnica sin nueva política de negocio. Diseño y orden de bloqueos: [ADR 0050](adr/0050-idempotencia-y-reconciliacion-de-documentos.md).

## Cortes y trabajo pendiente

1. **M06 verificado localmente:** PDF, intención mínima compartida e inspección real de contenido; evidencia final y límites detallados abajo.
2. **M05 siguiente:** extender a manifiesto foto/miniatura, metadatos/anclaje y recuperación parcial de dos objetos; preservar mantenimiento.
3. **M09 siguiente:** identidad retenida archivo→inspección, recuperación de baja parcial y replay 204 autorizado después de ausencia. DELETE actual sigue sin idempotencia por encabezado.

T4 y T5 global no forman parte de este corte. Cada entrega conserva código, pruebas, docs y migración propios; no se propone un commit de pruebas aislado como entrega funcional.

## Evidencia observada

- RED integral inicial, antes de producción: `ArchivoIdempotenciaHttpTest`, 14 casos, 8 fallos, 0 errores/omitidos, exit 1, 21.840 s. Incluía ambas variantes foto/PDF y dos bajas.
- RED M06 ampliado, antes de producción: `ArchivoDocumentoIdempotenciaHttpTest`, 16 casos reportados, 9 fallos, 0 errores, 2 omitidos M09, exit 1, 23.322 s. Las variantes M05 se retiraron de la selección activa, no se presentan como verdes.
- Primer intento GREEN: mismo runner, timeout a 30 s sin resultado final. Se observaron efectos de la limpieza JPA: la nueva transacción exterior necesitaba flush de archivo antes de `registrarActividadAutor`, que limpia el contexto. Se añadió ese flush; el timeout no acreditó GREEN ni fracaso definitivo de todas las pruebas.
- Recheck posterior ejecutado por el parent y comunicado a este worker: exit 1 en 31 s, **no timeout**; 19 casos, 1 fallo, 0 errores, 2 omitidos M09. Los otros 16 activos pasan provisionalmente. Único fallo: `claveConIntencionPendienteNoPuedeReutilizarseEnEnvio`, 422 obtenido frente a 409 esperado. Este es el RED observado del bloqueo entre handlers.
- Primera corrección de ese bloqueo: puerto genérico consultado dentro de la frontera serializada G7.1 y adaptador de intención PDF. Añadida regresión del núcleo para huella/versión, aislamiento, bytes/status previos y consulta bajo advisory lock. El parent acreditó después job2 GREEN: exit 0/52 s, 132 casos, cero fallos/errores/omitidos (17 PDF y regresiones G7.1/handlers previos).

Las pruebas operan por MockMvc sobre Spring y PostgreSQL/MinIO reales. Las instantáneas SQL/listados S3 observan efectos; las operaciones de dominio entran por HTTP. La prueba incierta induce excepción después de un PUT MinIO real mediante spy: no es corte físico de red. La prueba de rollback impone un CHECK PostgreSQL al insertar el resultado. Las identidades de prueba no son JWT reales.

## Verificación y límites

Con JDK 25, ejecutar primero `./mvnw -B -ntp -Dtest=ArchivoDocumentoIdempotenciaHttpTest test`. Luego regresión focalizada de archivos, envío, OpenAPI y `EvidenciaYCierreTest`; generar diccionario con `./mvnw -Dtest=DiccionarioDeDatosTest test`. No editar el diccionario manualmente.

**Selección M06:** las funciones `pendienteM09BajaReproduce204TrasAusenciaSinTocarActividadNiObjetos` y `pendienteM09BajaRetenidaRevalidaAutorActualDelPadre` conservan todas las aserciones iniciales en la base, pero no llevan anotaciones JUnit ni se heredan como tests omitidos. El runner PDF seleccionó 17 reglas activas con cero omitidos en job2. La corrección B1 añade dos reglas activas, aprobadas en job5 y reverificación independiente. M05 sigue fuera de esa selección y M09 requiere activación/implementación en su corte, no se declara aprobado.

Antes de B1, el parent comunicó job3 GREEN: 104 pruebas compartidas y comandos globales/gates/diccionario, todos exit 0. El verificador independiente ejecutó además 49 + 61 pruebas aprobadas; sus tres sondas adicionales tuvieron un único fallo B1. Las otras dos sondas (PDF pendiente con otra/sin clave y MIME remoto incorrecto) pasaron.

**B1 observado:** tras fallo antes de PUT y reserva PDF durable, foto histórica con el mismo UUID obtuvo 201/JPEG y dos objetos; el retry PDF terminó admitiendo JPEG para su huella. La corrección reutiliza la comprobación de reserva en el creador común de fotos y coordina mantenimiento mediante el mismo bloqueo UUID. `reservaPdfPendienteImpideFotoEnCualquierPadreYPermiteRecuperarPdf` exige 409/problem sin cambios en filas, auditoría, contadores y objetos/bytes/MIME para los tres padres; después exige recuperación PDF íntegra con un objeto y resultado keyed reproducible. `uuidConfirmadoConservaDeduplicacionHistoricaEntreTipos` fija la preservación. **Corrección aprobada en job5 y reverificación independiente.**

Resultado final del corte M06, posterior a B1:

- Job5: 106/106 pruebas focalizadas y 1706/1706 globales, sin fallos, errores ni omitidos; Spotless/package/PMD, JaCoCo y diccionario generado sin nuevo diff, todos exit 0.
- Reverificación limitada: sonda B1 original más recuperación y compatibilidad entre tipos, 3/3 sin omitidos, exit 0.
- Diferencial contra `64e0ebf`: ambos Maven exit 0, 95 observaciones HTTP. 90 coinciden con exclusiones documentadas de nombre de objeto, puerto/firma y timestamps del cierre; cinco errores 400 de binding conservan status, tipo, demás campos y multiconjunto completo de errores, pero cambia el orden de `errores`. No se afirma igualdad byte a byte de esos cinco rechazos no cacheables. El replay 201 del candidato se verifica RAW, sin normalización.
- El primer harness diferencial falló al compilar en ambas versiones por el tipo de builder multipart; se corrigió solo el harness. Su recheck ejecutó ambas versiones y dejó las cinco diferencias de orden anteriores, diagnosticadas sobre evidencia conservada, sin reejecutar Maven ni alterar producto.

Los párrafos anteriores documentan la secuencia RED. Estos resultados finales no prueban JWT real, corte físico de respuesta, espera exitosa de replay con pérdida de titularidad, M05/M09 ni aceptación integral. Las pruebas globales se vuelven a evaluar al integrar los cortes restantes de T3.
