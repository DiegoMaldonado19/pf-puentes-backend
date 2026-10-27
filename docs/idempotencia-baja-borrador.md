# M08 — Baja idempotente de borrador

**Etapa actual: GREEN local y diferencial observados por el padre; dictamen independiente pendiente.** RED observado: job1 exit1/39 s, 72 pruebas/15 fallos/cero errores u omisiones; M08 18/14 fallos, OpenAPI un fallo, informes Surefire: borrador 25/25 y envío 22/22. No acredita cierre de T4, sincronización frontend ni T5. Autoridad: S1–S3 y L29 de `odd/tasks/g7-2-completo.md`; decisión en [ADR 0053](adr/0053-idempotencia-baja-borrador.md).

## Petición y resultado

`DELETE /api/v1/inspecciones/{id}` conserva los roles ESTUDIANTE, PROFESIONAL_EXTERNO y CATEDRATICO y la exigencia de autor propio. `Idempotency-Key` es opcional: ausente o blanca conserva el comportamiento anterior (primera baja 204; repetición 404; ninguna reserva). Con clave no blanca se aplica `strip()`, máximo 100 puntos de código Unicode; 101 devuelve 400 `idempotencia-invalida` sin efectos.

La huella v1 de M08 es SHA-256 del JSON determinista UTF-8 con propiedades ordenadas `method` y `path`: `{"method":"DELETE","path":"/api/v1/inspecciones/<UUID>"}`. No incluye un DTO inexistente, fecha del servidor ni estado mutable. Query, cuerpo no vinculante y X-Sgp-Sync no adquieren semántica nueva. Otro UUID u operación con la misma clave del mismo usuario devuelve 409 `clave-idempotencia-incompatible`, sin ejecutar ni sobrescribir. Las filas heredadas JSONB no verificables también se rechazan. El espacio de claves sigue siendo por usuario, no global.

Solo se guarda 204: **204 vacío**, cero bytes y ningún Content-Type, tanto al ejecutar como al reproducir. No se reconstruye un DTO ni se serializa `null`. Espera máxima por clave: **2 s**; agotarla devuelve 409 `operacion-en-proceso`, sin resultado guardado y con posibilidad de reintento. No se cachean binding, 400, 403, 404, 409 de estado ni fallos transitorios/500.

## Autorización y evidencia después de la baja

La evidencia es la misma fila original de `inspeccion`, conservada por la baja lógica. No basta conocer UUID/clave ni haber sido autor anteriormente. Comprobar rol admitido y **autor actual** antes de acceder al resultado y después de esperar/ejecutar. Usar una variante específica de autorización sobre `findById` que incluya eliminadas, sin relajar `propio` para PUT, envío u otros handlers.

La propiedad sobre fila eliminada solo habilita consultar el resultado de la clave/huella. Si no existe un resultado admitido para esa operación, el callback reusa `propioEnBorrador`: recurso ajeno, ausente o ya eliminado da 404; no borrador da 409 `inspeccion-no-editable`. Así una nueva clave después de la baja no inventa otro 204. Un replay ya confirmado no exige editabilidad, no restaura el estado, no renueva fechas y no repite auditoría. Si cambia el autor, el antiguo recibe 404 incluso con su clave original. Un rol excluido recibe 403.

No se incorpora una nueva comprobación de asignación, puente activo o formulario a DELETE: se conservan las reglas existentes de esta operación. La seguridad de petición sigue validando sesión/rol/estado por sus mecanismos vigentes.

## Atomicidad y carreras

La entrada idempotente no lleva transacción ambiente. El callback participante del núcleo debe cargar y validar la fila vigente y llamar al bean `EscrituraBorradorInspeccion.eliminar`, no copiar su escritura ni insertar auditoría directamente. Baja lógica, incremento de versión, auditoría DAR_DE_BAJA y resultado se confirman o revierten juntos. No tocar acompañantes, archivos/MinIO, transiciones, copias, avisos ni actividad de autor como efecto adicional.

Duplicados concurrentes con igual usuario/clave se serializan en G7.1: un solo cambio de versión, auditoría y resultado. Claves distintas y escritores sin clave siguen sujetos a `@Version`; no hay un bloqueo de recurso implícito en el advisory lock. Si avance o envío confirman después de que DELETE leyó BORRADOR, la baja no puede sobrescribirlos: conservar el fallo optimista histórico 500 no cacheable y rollback integral de la perdedora. El retry puede borrar el avance vigente si sigue siendo borrador, pero después de envío devuelve 409. Si la baja ya confirmó, avance/envío posteriores conservan 404. El replay de la baja no vuelve a realizar esas validaciones de editabilidad.

## Pruebas y evidencia local

`InspeccionBajaIdempotenciaHttpTest` usa MockMvc y `PruebaIntegracion`, con PostgreSQL real. Prepara datos por los fixtures existentes y no llama servicios internos para ejecutar DELETE. Comprueba status/cuerpo/Content-Type, estado conservado, versión, relaciones, contadores de auditoría/resultados/transiciones/copias/notificaciones y ausencia de mutaciones ante rechazo. Incluye replay exacto/tardío, autorización actual, otro recurso/operación/legado, compatibilidad sin clave/blanca, límites Unicode, errores no cacheables, CHECK real al guardar resultado, duplicados concurrentes y espera por clave.

Las carreras de avance y envío ejecutan PUT/POST HTTP sin clave dentro de la transacción rival que ya posee la fila, mientras DELETE espera en PostgreSQL real. El envío usa datos completos y cuatro filas de foto como el fixture publicable existente; ejecuta validación, transición y notificaciones reales, sin objetos MinIO nuevos. Se compara la instantánea del commit rival con el estado posterior al rollback de DELETE. Las bajas con claves distintas y con un rival sin clave deben confirmar una sola versión/auditoría y revertir a la perdedora; la pérdida de autor durante espera de clave se comprueba tanto para ejecución nueva como replay. Barreras: `pg_locks` y observación del intento advisory en `pg_stat_activity` con limpieza del snapshot de monitorización. El padre ejecutó el candidato y ampliaciones: job2 exit1/40 s, 76 pruebas, un fallo, cero errores/omisiones; único fallo en la carrera de envío, por contador de notificaciones esperado 0 frente a 1. Diagnóstico: `EnvioInspeccionService.persistirEnvio` llama síncronamente a `AvisoInspeccionEnviada.avisar`; `NotificacionService.notificar` participa en la transacción y usa `save`, sin flush. La instantánea JDBC tomada antes del commit no veía ese INSERT pendiente de JPA; no era una notificación asíncrona ni efecto de la baja perdedora. Primera corrección acotada: hacer `EntityManager.flush()` dentro de la transacción rival antes de la instantánea esperada, exigir incremento de exactamente una notificación para envío (cero para avance) y estado HTTP ENVIADA. Se conserva la igualdad íntegra de las ocho dimensiones después del rollback, incluido el contador, así como la ausencia de baja y resultado. Recheck del padre job3 exit0/207 s: 76/76 focalizadas y 1781/1781 globales sin fallos/errores/omisiones; Spotless apply/check, empaquetado, PMD, tres gates JaCoCo y comprobaciones de diccionario sin cambios/diff aprobados. Verificación independiente `mvqxplxy-4-skic` aprobada para S1–S3/T4, sin bloqueantes: job5 exit0/30 s, 25/25 (22 pruebas M08 y tres sondas independientes), cero fallos/errores/omisiones. Las sondas comprobaron aislamiento usuario/clave, identidad original tras transferencia y replay tardío, con SHA-256 de filas completas de ocho tablas. No se afirma cobertura JWT real, revocación de rol en BD durante petición, pérdida física de respuesta o corte de proceso.

`InspeccionOpenApiTest` exige clave opcional documentada y 204 sin esquema de contenido. El controlador documenta autorización, espera, compatibilidad y resultado; los handlers hermanos mantienen sus helpers y allowlists.

Runner focalizado utilizado por el padre; se conserva para reproducción explícita, no como instrucción de reintento automático:

```sh
JAVA_HOME=/home/achess/.sdkman/candidates/java/25.0.5.0+8-tem PATH=/home/achess/.sdkman/candidates/java/25.0.5.0+8-tem/bin:$PATH ./mvnw -B -ntp -Dtest=InspeccionBajaIdempotenciaHttpTest,InspeccionOpenApiTest,InspeccionBorradorTest,InspeccionEnvioTest test
```

El RED anterior fue observado por el padre antes de implementar; el GREEN posterior comprende 76 ejecuciones (M08 22, OpenAPI 7, borrador 25, envío 22). Este worker no ejecutó Maven ni el diferencial: la evidencia de runners corresponde al padre.

### Diferencial y límites de comparación

El harness `odd/scratch/g7-2-t4/run.py` exporta base y candidato dentro del scratch ignorado, captura stdout/stderr/exits y respuestas HTTP RAW y clasifica cambios solicitados frente a regresiones. Comando ejecutado por el padre: `python3 odd/scratch/g7-2-t4/run.py`. Job4 exit0/54 s, Maven base/candidato exit0: **38 observaciones iguales, cinco cambios solicitados y cero regresiones**. Artefacto: `odd/scratch/g7-2-t4/ejecucion-20261027T170235266258Z/comparacion.json`.

Los cinco cambios son replay keyed 404→204, clave de 101 caracteres 204→400, clave de avance reutilizada para DELETE 204→409 y los dos GET derivados de rechazar esas bajas. Se compara JSON estructuralmente excluyendo solo `creado_en`, `datos_permanentes_confirmados_en`, `sincronizada_en` y `enviada_en`, generados por reloj del servidor. No se ordenan arrays ni se declara igualdad RAW de JSON entre versiones con fechas dinámicas. Sí se exige RAW para cada replay contra su propio ACK; el 204 conserva cero bytes y ausencia de Content-Type. stdout/stderr y exit codes Maven permanecen disponibles, sin afirmar igualdad byte a byte de sus logs.

### Frontera de la unidad

ASSESS comunicado por el padre sobre nueve archivos staged/893 líneas: nativeRisk medium, reviewDue false/under_budget y escalación item2 con verificación independiente requerida. No hubo START ni consumo de autoridad nativa. La unidad es coherente: comportamiento, contrato y prueba HTTP determinista permanecen juntos (la prueba concentra aproximadamente 694 líneas); no se separan para satisfacer un objetivo de 400 líneas.

El dictamen independiente aprueba T4/M08 sin bloqueantes; no es una aprobación nativa ni aceptación externa. La matriz y el plan global permanecen en T5, fuera de esta unidad, así como sincronización frontend. No hubo publicación.
