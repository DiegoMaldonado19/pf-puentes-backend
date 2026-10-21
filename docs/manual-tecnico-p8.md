# Manual técnico de P8 — Índice de Condición y mantenimiento

Actualizado: 21/10/2026. Describe el código disponible; la integración completa sigue pendiente. Trazabilidad: RN-IC-01–08, RN-MTO-01–09 y DT-CAL-08. Las HU/documentos personales fuera de este repositorio no acompañan al código cuando el equipo lo descarga; este manual sí puede compartirse mediante el commit/PR.

## 1. Módulos y límites actuales

| Pieza | Disponible | Pendiente |
|---|---|---|
| `indice/` | Núcleo puro, pesos normalizados, repositorios, creación Java autorizada/auditada, tres GET Admin | Adaptación real P4, publicación P5, confirmación/recálculos y creación HTTP |
| `mantenimiento/` | Máquina, origen orden/puente/inspección, consultas de historial/detalle/propuestas, PATCH autorizados/auditados de prioridad y fecha programada y POST de aceptación/inicio/cierre/descarte y carga/consulta paginada de evidencia P1; alerta diaria de inspección vencida mediante P9 | Cancelación, sugerencia automática, generación por catálogo y job rutinario anual |
| Frontend P8 | Componente de condición, consulta de pesos, revisión/aceptación, bandeja, contenedor HTTP de propuestas e historial/detalle con inicio, evidencia, cierre, descarte, ajuste de prioridad y programación de fecha opcionales | Sesión/guardias P2, rutas y conexiones pendientes; las piezas no aparecen aún en la página inicial |

Se emplean DTO y MapStruct; las entidades JPA no se exponen por HTTP. La autorización efectiva vive en backend. La existencia de una validación interna o una columna no equivale a una operación HTTP habilitada.

## 2. Integración del cálculo con P5

`CalculadoraIndiceCondicion` es Java puro, sin repositorios, sesión o HTTP. Recibe calificaciones enteras 0–4 y pesos positivos en `CalificacionElemento`; `CondicionesCriticas` recibe cuatro banderas normalizadas. Devuelve `ResultadoIndiceCondicion` con `indiceCondicion`, `estadoCalculado` y todas las `anulaciones`.

```java
// Ejemplo didáctico: estos pesos no constituyen calibración oficial.
var resultado = new CalculadoraIndiceCondicion().calcular(
    List.of(
        new CalificacionElemento(0, new BigDecimal("2")),
        new CalificacionElemento(4, BigDecimal.ONE)),
    new CondicionesCriticas(false, false, false, false));
// IC = 100 * (1 - 4 / 12), aproximadamente 66.6667; estado REGULAR.
```

La fórmula implementada es `100 × (1 − Σ(calificación × peso) / (4 × Σpeso))`. Se comparan umbrales sin redondeo visual: BUENO desde 75, REGULAR desde 50 y MALO por debajo de 50. La división final utiliza DECIMAL128; la precisión de persistencia y sus límites todavía deben acordarse con el flujo de publicación.

Las cuatro banderas significan exactamente:

1. Socavación **con exposición de cimentación** en estribo o pila.
2. Asentamiento de estructura.
3. Elemento portante cortado o con pérdida de sección.
4. Ausencia total de un elemento portante.

Cualquier causa fuerza MALO y conserva el IC numérico. El conjunto contiene todas las causas, sin prioridad entre ellas. La entidad de inspección actual conserva una sola anulación: el adaptador no debe elegir una arbitrariamente para ocultar esa diferencia.

La fachada conceptual `calcular(datos, versionFormulario)` del acuerdo de equipo todavía necesita el contrato real de P4. El núcleo anterior no interpreta JSON Schema ni identifica daños por rutas inventadas. P5 debe obtener entradas confiables, calcular y guardar el resultado oficial al publicar; la llamada y persistencia reales aún no están conectadas. Un borrador no debe presentarse como resultado oficial. `Sin evaluar` lo representa el componente frontend cuando no existe una evaluación aplicable; no se calcula asignando cero a datos ausentes.

Detalle: [ADR 0016](adr/0016-nucleo-puro-del-indice-de-condicion.md).

## 3. Configuración de pesos

`ConfiguracionPesosService.crear(JuegoPesos, ElementosDeDano)` es una operación Java transaccional solo de ADMINISTRADOR. Valida existencia de la versión del formulario y pertenencia de las referencias a metadatos normalizados de esa misma versión, crea un juego nuevo y audita juego/pesos por fila mediante P2. No modifica el juego anterior.

`ElementosDeDano` debe proceder de metadatos del servidor; **no puede recibirse como lista permitida declarada por el navegador**. Falta su extracción real desde P4. Los pesos se validan positivos y exactamente representables en `NUMERIC(6,3)`, con referencias no duplicadas. La referencia de acta es opcional para datos provisionales; una referencia presente no certifica una firma.

La API de lectura no selecciona automáticamente un juego activo ni define la política de versiones. Detalle: [ADR 0027](adr/0027-validacion-de-configuraciones-de-pesos.md), [ADR 0019](adr/0019-creacion-transaccional-y-auditada-de-pesos.md).

## 4. API disponible y prueba con Postman

En local: API `http://localhost:8080`; Swagger `http://localhost:8080/api/docs/swagger-ui.html`; especificación JSON `/api/docs`. Arranque y variables: [README](../README.md#desarrollo-local).

1. Inicia sesión con una cuenta real y activa mediante `POST /api/v1/auth/login`, cuerpo JSON con `correo` y `contrasena`.
2. Usa `access_token` de la respuesta como Bearer Token en Postman. El token de renovación se gestiona mediante la cookie de P2; no se incorpora a estos payloads.
3. Usa IDs de datos existentes. Un UUID inventado con formato válido puede producir 404; las pruebas no instalan datos de demostración en producción.

| Método y ruta | Permiso | Respuesta |
|---|---|---|
| `GET /api/v1/formularios/{formularioId}/juegos-pesos` | ADMINISTRADOR | Página de juegos de esa versión de formulario, por ID descendente |
| `GET /api/v1/juegos-pesos/{id}` | ADMINISTRADOR | Juego: `id`, `formulario_version_id`, `acta`, `vigente_desde` |
| `GET /api/v1/juegos-pesos/{id}/pesos` | ADMINISTRADOR | Página de elementos: `id`, `elemento_ref`, `peso`; referencia ascendente |
| `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento` | CATEDRATICO o ADMINISTRADOR | Historial paginado de todos los estados del puente, con `puente_id`, ID descendente |
| `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}` | CATEDRATICO o ADMINISTRADOR | DTO de detalle; 404 si pertenece a otro puente o no existe |
| `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/propuestas` | CATEDRATICO o ADMINISTRADOR | Página de PROPUESTA del puente por ID descendente, con `puente_id` también si está vacía |
| `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/aceptacion` | CATEDRATICO o ADMINISTRADOR | DTO de la orden aceptada, estado PROGRAMADA |
| `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/inicio-ejecucion` | CATEDRATICO o ADMINISTRADOR | DTO persistido EN_EJECUCION; solo desde PROGRAMADA |
| `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/evidencias` | CATEDRATICO o ADMINISTRADOR | Multipart foto/miniatura, 201 ArchivoDTO; PROGRAMADA o EN_EJECUCION |
| `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/evidencias` | CATEDRATICO o ADMINISTRADOR | Página de JPEG/WebP no purgadas, incluso después de cerrar |
| `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/cierre` | CATEDRATICO o ADMINISTRADOR | JSON fecha_ejecucion/responsable, 200 DTO EJECUTADA; requiere foto guardada de esta orden |
| `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/descarte` | CATEDRATICO o ADMINISTRADOR | JSON motivo obligatorio, 200 DTO DESCARTADA; solo PROPUESTA, sin borrar historial |
| `PATCH /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/prioridad` | CATEDRATICO o ADMINISTRADOR | JSON prioridad/justificacion, 200 DTO persistido; solo estados abiertos, conserva sugerida |
| `PATCH /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/fecha-programada` | CATEDRATICO o ADMINISTRADOR | JSON fecha_programada obligatoria, 200 DTO persistido; solo PROGRAMADA, conserva estado |

Los GET paginados aceptan `page` desde 0 y `size` entre 1 y 100; valores predeterminados 0/20. La salida tiene `content`, `page`, `size`, `total_elements`, `total_pages`. Una colección existente sin registros devuelve página vacía; recurso inexistente, 404.

Aceptación e inicio usan **Body: none**. La aceptación requiere PROPUESTA; el inicio requiere PROGRAMADA, siempre del puente indicado. Ambas operaciones conservan fecha programada e intervención. No crea propuestas ni permite elegir un estado arbitrario. Aceptar una orden sin fecha no acredita la programación rutinaria anual. Pueden verificarse el estado retornado y el rechazo 409 de un segundo intento; el segundo intento no duplica auditoría.

Errores en RFC 7807, con `type`, `title`, `status`, `detail` e `instance`:

| Código | Significado en estas operaciones |
|---|---|
| 400 | UUID, metadatos/cuerpo obligatorio o parámetros de página inválidos |
| 401 | Sin sesión o token inválido |
| 403 | Rol sin permiso |
| 404 | Formulario/juego/puente inexistente, u orden inexistente en el puente solicitado |
| 409 | Estado incompatible con la operación o UUID de archivo usado en otro padre |
| 413 | Archivo/petición supera el límite HTTP configurado |
| 422 | Origen incompatible, firma de foto inválida, falta de fotografía guardada o responsable mayor de 150 caracteres Unicode |

Propuestas, historial completo y detalle tienen GET para Catedrático y Administrador, con confirmaciones del grupo comunicadas por el usuario. Añade `puente_id` al contrato de página. También se confirmaron los actores de evidencia y cierre y su carga en PROGRAMADA/EN_EJECUCION; están implementados. El grupo confirmó también Catedrático/Admin para descarte, ya implementado. El usuario adoptó actores/estados para ajuste manual de prioridad, también implementado. Siguen pendientes creación HTTP de pesos, sugerencia automática de prioridad, cancelación y recálculo HTTP. El usuario adoptó la regla de programación de fecha descrita debajo; ya está implementada. El permiso de lectura no asigna actores de escritura. El permiso de aceptación de RN-MTO-02 no se convierte automáticamente en una matriz completa de lectura o ejecución.

Detalle: [ADR 0020](adr/0020-consulta-paginada-de-juegos-de-pesos.md), [ADR 0025](adr/0025-aceptacion-autorizada-y-auditada-de-propuestas.md).

## 5. Órdenes, transacciones y evidencia

La máquina interna admite PROPUESTA → PROGRAMADA, PROPUESTA → DESCARTADA, PROGRAMADA → EN_EJECUCION y EN_EJECUCION → EJECUTADA. CANCELADA puede leerse como histórico, pero su origen sigue pendiente y ninguna transición hacia ella está habilitada. `OrdenMantenimiento.cambiarEstado` valida antes de mutar; no existe setter público de estado.

Cerrar exige fecha, responsable no vacío y una foto guardada. El contador de `DatosTransicionOrden` es interno: la fachada lo obtiene con `ArchivoService.contarEvidencias(ordenId)`, exclusivamente JPEG/WebP no purgadas de esa orden. P8 conecta autorización, estado, subida, consulta paginada y cierre; conserva las firmas públicas del contrato 16 y añade la consulta paginada. Una foto seleccionada cuya carga no terminó no satisface el mínimo.

`OrdenMantenimientoService.aceptar` bloquea por orden y puente antes de llamar al bean auditado. Así el aspecto de P2 toma una instantánea anterior coherente con el bloqueo; cambio y auditoría se confirman o revierten juntos. Dos aceptaciones concurrentes producen una aceptación y un 409. No se escriben auditorías directamente desde P8.

`ConsultaOrdenes` es interno y devuelve DTO en transacción de solo lectura. `listarPropuestas` filtra PROPUESTA en base de datos, incluidos sus totales; la fachada autorizada `ConsultaOrdenesService` expone historial y detalle sin auditoría de escritura. Detalle en [ADR 0028](adr/0028-historial-y-detalle-autorizado-de-ordenes.md). El repositorio no expone borrado físico. La inspección de origen es opcional; si existe, el validador comprueba que corresponda al mismo puente.

`ajustarPrioridad` conserva la sugerida y exige justificación no blanca para cambiar la vigente; no implementa la fórmula de sugerencia IC/peso ni tiene endpoint. Los roles de cierre/evidencia, otros ajustes y las reglas temporales se mantienen pendientes donde el enunciado no los especifica.

Detalle: [ADR 0021](adr/0021-nucleo-de-transiciones-de-ordenes.md), [ADR 0022](adr/0022-persistencia-y-consulta-base-de-ordenes.md), [ADR 0023](adr/0023-validacion-del-origen-de-ordenes.md), [ADR 0024](adr/0024-ajuste-justificado-de-prioridad-de-ordenes.md).

Inicio de ejecución: `OrdenMantenimientoService.iniciarEjecucion` reutiliza la carga/validación bloqueada y llama al bean `InicioEjecucionOrdenService` auditado. Solo PROGRAMADA → EN_EJECUCION, sin modificar fechas; actor/fecha/cambio quedan en auditoría. Dos inicios concurrentes producen 200/409 con una sola auditoría. Fallar la bitácora revierte el estado. El frontend habilita la acción con `puedeIniciar` explícito y confirma fila/detalle desde el POST, sin reintentar escrituras; sigue pendiente el host protegido de P2/P3. [ADR 0029](adr/0029-inicio-auditado-de-ejecucion-de-ordenes.md).

### Prueba de evidencia y cierre con Postman

Con Bearer de Catedrático/Admin y una orden real PROGRAMADA o EN_EJECUCION del puente:

1. `POST .../{ordenId}/evidencias`, Body → form-data. Campos **File**: `foto` y `miniatura` (JPEG/WebP, foto comprimida y miniatura de 300 px). Campos **Text**: `id` (UUID v7 nuevo del cliente), `capturada_en` (ISO UTC, por ejemplo `2026-10-20T12:00:00Z`). Opcionales: `elemento_ref`, `latitud`, `longitud`. Deja que Postman genere Content-Type con su boundary.
2. Comprueba 201, ID y URL firmadas; abre la miniatura/foto. Reenviar el mismo UUID en esa misma orden editable devuelve lo guardado sin duplicarlo. `GET .../{ordenId}/evidencias?page=0&size=20` devuelve solo fotos de esa orden; renovar esa consulta renueva las URL temporales.
3. Si estaba PROGRAMADA, inicia con el POST sin cuerpo documentado arriba. Luego `POST .../{ordenId}/cierre`, Body → raw → JSON:

```json
{
  "fecha_ejecucion": "2026-10-20",
  "responsable": "Cuadrilla responsable de la intervención"
}
```

4. Comprueba 200 y EJECUTADA, fecha/responsable persistidos; la consulta de evidencia sigue disponible. Cerrar sin foto guardada de esa orden: 422; repetir cierre o subir tras cerrar: 409. No se acepta un contador de fotos enviado por el navegador.

Subida y cierre comparten bloqueo de fila; un cierre espera la carga en curso y evalúa su resultado confirmado. Cambio y bitácora SQL son atómicos. MinIO/S3 no participa en la transacción SQL de P1: fallos entre objetos o posteriores pueden dejar objetos sin fila confirmada; no se cuentan como evidencia. No hay máximo de fotos por orden, restricción temporal de ejecución ni notificación de cierre añadidos por P8. [ADR 0030](adr/0030-evidencia-y-cierre-autorizado-de-ordenes.md).

### Prueba de descarte con Postman

Con sesión real Catedrático/Admin, consulta primero una orden en PROPUESTA del puente. Envía `POST .../ordenes-mantenimiento/{ordenId}/descarte`, Body → raw → JSON:

```json
{
  "motivo": "La intervención ya está incluida en otra propuesta."
}
```

Comprueba 200, DESCARTADA y el motivo persistido. El GET de detalle/historial conserva la orden; el GET de propuestas ya no la incluye. Repetir da 409 sin añadir auditoría. Un motivo ausente/vacío da 400. No se fija máximo por el límite de otra columna: el motivo de la orden es TEXT y se incluye en la instantánea auditada del cambio. No se habilita CANCELADA. [ADR 0031](adr/0031-descarte-auditado-de-propuestas.md).

### Prueba de ajuste manual de prioridad con Postman

Usa una orden real PROPUESTA/PROGRAMADA/EN_EJECUCION del puente con sesión Catedrático/Admin. Envía `PATCH .../ordenes-mantenimiento/{ordenId}/prioridad`, Body → raw → JSON:

```json
{
  "prioridad": "ALTA",
  "justificacion": "Se requiere atención urgente a la intervención."
}
```

Elige un valor distinto del vigente para comprobar el cambio. Verifica 200, prioridad/justificacion_prioridad persistidas y prioridad_sugerida/estado/demás datos conservados. Un cambio sin justificación devuelve 422. Repetir la vigente devuelve lo guardado sin reescribir la razón ni crear auditoría. Una orden ejecutada/descartada/cancelada devuelve 409, también al pedir el mismo valor. La ventana y actores proceden de la decisión del usuario donde RN-MTO-06 no los enumera; no se atribuyen literalmente al enunciado.

La justificación permanece en su columna TEXT y en la instantánea auditada saneada. No se calcula prioridad sugerida ni se exige tener IC/elemento/sugerencia para realizar el ajuste manual. [ADR 0032](adr/0032-ajuste-autorizado-de-prioridad-de-ordenes.md). El frontend incorpora selector y justificación al detalle con permiso explícito; `operacionPendiente()` incluye el ajuste y `(prioridadAjustada)` informa a otros consumidores. Falta el acceso integrado P2 y conectar P8 con el inventario P3 disponible.

### Prueba de programación de fecha con Postman

Con el Bearer de una cuenta activa Catedrático/Admin, consulta una orden del puente en PROGRAMADA. Si tienes una propuesta existente, acéptala primero mediante el POST sin cuerpo de aceptación. Envía PATCH a `/api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/fecha-programada`, Body → raw → JSON:

```json
{"fecha_programada":"2026-11-02"}
```

Verifica 200 y la fecha exacta en el DTO y en GET de detalle; conserva PROGRAMADA, prioridades, origen y demás datos. Cambia la fecha para comprobar una segunda programación auditada. Repetir la misma no reescribe actualizado_en ni añade auditoría. No se envía fecha_ejecucion, responsable ni estado; programar fecha no inicia ejecución. El JSON requiere fecha civil `AAAA-MM-DD`, no un instante con hora que pueda perderse silenciosamente.

Fecha ausente/nula/vacía, inexistente o formato incorrecto: 400. Orden de otro puente/ausente: 404. Cualquier estado distinto de PROGRAMADA: 409, incluso al repetir una fecha. Origen inconsistente: 422. Sin sesión/token inválido/cuenta inactiva: 401; rol vigente ajeno: 403. No hay mínimo ni máximo temporal adicional: la decisión del usuario admite fechas pasadas o futuras. No permite borrar la fecha.

Este PATCH resuelve la edición de programación de MTO-03. RN-MTO-07 requiere además asegurar la programación rutinaria anual por puente; todavía faltan consultas, políticas y automatización correspondientes. No confundir aceptar sin fecha, registrar una fecha o emitir recordatorio con garantizar esa periodicidad. [ADR 0033](adr/0033-programacion-auditada-de-fecha-de-ordenes.md).

En frontend, `ConsultaOrdenes` recibe `puedeProgramar` de la sesión (false por defecto), incorpora el formulario y emite `fechaProgramada` confirmado; `operacionPendiente()` bloquea las otras acciones mientras guarda. El inventario P3 existe; aún falta conectar los componentes privados con su selección y la sesión/guardias frontend de P2.

## 6. Conexiones pendientes del equipo

| Responsable | Entrega necesaria para completar el flujo |
|---|---|
| P4 | JSON Schema real, respuestas de ejemplo, referencias/metadatos de daño, severidades y portantes; acuerdo de versiones con pesos |
| P5 | El record InspeccionPublicada ya llegó con P3; faltan publicación real que calcula/guarda IC/versiones y emisor en esa transacción |
| P3 | Inventario/listado/mapa/ficha y uso de estado P8 ya recibidos; conectar los contenedores P8 al puente seleccionado disponible |
| P2 | Sesión, interceptores y guardias frontend; aplicar los mecanismos backend existentes en cada nueva operación de P8 |
| P1 | Almacenamiento/captura reutilizados para evidencia de órdenes; conservar contrato 16 |
| P9 | Emisor ya integrado con alerta de inspección vencida; falta su presentación frontend y la integración independiente del rutinario anual |
| Equipo/catedrático | Actores pendientes de otras operaciones, catálogo daño → intervención, calibración/acta, prioridad, precisión/anulaciones y reglas temporales no especificadas |

El evento y las políticas pendientes son acuerdos por integrar, no clases/rutas ya disponibles. La generación de propuestas, recálculo, confirmación y job rutinario anual no se dan por implementados por existir tablas o enum. El job de inspección vencida sí está implementado, según la sección siguiente.

### Alerta por inspección vencida (RN-MTO-08)

P8 ejecuta `AlertasInspeccionJob` diariamente a las 06:00 America/Guatemala, con ShedLock y lotes de 100 puentes/destinatarios. El usuario adoptó: Catedrático/Admin activos; doce meses desde la última publicación; desde el registro si nunca hubo una; un aviso por puente/destinatario mientras ese período permanezca vencido. Una nueva publicación inicia otro plazo. Se suman meses de calendario en UTC y se compara estrictamente; el límite exacto y los borradores no provocan/reinician el aviso. No se usa fecha_ultima_inspeccion ni se presupone que represente publicada_en.

La notificación de P9 contiene tipo INSPECCION_VENCIDA, código/nombre del puente y ruta `/puentes/{id}` de P3. Para consultar con Postman: inicia sesión con el destinatario activo, usa su Bearer y solicita `GET /api/v1/notificaciones?page=0&size=20`; la lista se llama **contenido**, con campos tipo/mensaje/enlace. El servidor identifica al destinatario a partir de su sesión. Leer el aviso con el PATCH de lectura de P9 no habilita otro envío del mismo período.

No hay endpoint nuevo de alerta ni se modifican datos/fechas de producción para simular vencimientos. Las pruebas usan PostgreSQL y reloj controlado; pueden ejecutarse con `./mvnw -B -ntp -Dtest=AlertasInspeccionTest test`. Incluyen el GET real autenticado de P9. La presentación de notificaciones corresponde a P9; en el checkout frontend aún no existe esa pantalla. La ficha enlazada de P3 sí existe. No se creó una pantalla duplicada ni una sesión alternativa.

La migración `V2026_10_21_0100__crear_aviso_inspeccion_vencida.sql` añade marcas de envío y un índice de última publicación. Notificación y marca comparten transacción por puente: fallo revierte ambos, no bloquea otros puentes y se reintenta al día siguiente. Unicidad incluye el caso nunca publicado; concurrencia se protege con bloqueo por puente más ShedLock. No hay generación de órdenes, cambio de IC o correo añadido. [ADR 0034](adr/0034-alertas-de-inspeccion-vencida.md).

La política autorizada no incluye un filtro adicional de puentes por activo: se recorre el inventario permanente. La garantía de programación rutinaria anual (RN-MTO-07) conserva su incremento independiente y no se considera satisfecha por esta alerta.

## 7. Verificación y entrega

### Seguimiento rutinario anual: consulta y acciones existentes

Con una sesión Catedrático/Admin, usa Postman para `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/rutinario-anual?page=0&size=20`. Devuelve `fecha_evaluacion`, `fecha_referencia`, `origen_referencia` (EJECUCION_RUTINARIA o REGISTRO_PUENTE), `fecha_limite`, `programadas_en_plazo`, `sin_fecha`, `fechas_pasadas`, `posteriores_al_limite` y página `ordenes` con el contrato existente. Conteos globales independientes de la página, máximo 100 filas; 400 por UUID/página inválidos, 401/403 acceso y 404 puente ausente.

La base confirmada por el grupo es la última ejecución RUTINARIO/EJECUTADA o el registro sin ejecuciones. Otras tipologías, propuestas y fechas programadas no reinician el año. Se suma un año de calendario y se evalúa la ventana inclusiva hoy–límite. Registro/reloj UTC y fechas civiles sin segunda conversión de zona. Fecha pasada no equivale automáticamente a incumplimiento anual. Los indicadores de pasada y posterior al límite pueden coincidir; no se suman como un total. La consulta no confirma ejecución.

`ConsultaOrdenes` incorpora el panel con `[mostrarSeguimientoRutinario]="true"`, conservando `puedeConsultar` de la sesión. Seleccionar abre el detalle y sus acciones existentes; fecha guardada, inicio y cierre recargan el resumen desde el servidor. No se añade ruta pública: host con sesión/interceptores/guardias P2 y conexión privada P3 pendientes. No hay propuestas/avisos anuales o job nuevo en esta entrega. MTO-08 queda en progreso. [ADR 0035](adr/0035-consulta-del-seguimiento-rutinario-anual.md).

Verificación: **1105 pruebas backend**, 16 nuevas, sin fallos/errores/omisiones; compilación limpia, Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados (servicios 95.28 %, núcleo IC/máquina 100 % según clases configuradas). **387 frontend**, 21 nuevas, lint, Prettier, compilación estricta y build aprobados (~90.73 kB iniciales). Fuentes/pruebas idénticas verificadas en `/tmp/p8-rutinario-verificacion`, sin editar generados; no acredita CI remoto ni recorrido frontend autenticado completo. Los párrafos siguientes conservan evidencia histórica.

Verificación de **alerta de inspección vencida, 21/10/2026**: **1089 pruebas backend**, 17 nuevas sobre 1072; ninguna falla, error u omisión. Pasaron compilación limpia, Spotless, PMD, empaquetado y tres reglas JaCoCo: servicios 95.16 % de líneas, núcleo IC/máquina 100 % según clases configuradas y servicio de alertas 100 % de líneas/ramas. Incluye migración, PostgreSQL, P9, JWT, ShedLock real y escenarios de calendario, repetición, concurrencia, fallos y lotes. Verificada copia temporal de fuentes/pruebas idénticas sin interferencia del IDE, con reportes en `/tmp/p8-alertas-verificacion/target`. No se editan generados ni se rebajan controles. La primera corrida ajustó exclusivamente fixtures de baja P2 y expectativa JSON de P9; no cambió contratos o pruebas ajenas.

Frontend sin cambios en esta entrega: no se repiten sus pruebas/build; la última verificación del paso 6 sigue siendo 366 pruebas. Falta presentación P9 y recorrido con sesión P2/publicación P5 reales; resultados locales no acreditan CI remoto.

Verificación local del **21/10/2026, paso 6**: **1072 pruebas backend aprobadas**, 31 nuevas de programación sobre 1041 tras P2. Pasaron compilación limpia, Spotless, PMD, empaquetado y las tres reglas JaCoCo: servicios 95.07 % de líneas (mínimo 70 %) y núcleo IC/máquina 100 % según sus clases configuradas; el servicio nuevo de programación tiene 100 % de líneas y ramas. Las pruebas de programación incluyen JWT real, rol vigente y baja P2, estados/datos/fechas, auditoría/no-op, rollback, concurrencia y aceptación seguida de fecha. **366 frontend**, 41 nuevas sobre 325: pruebas DOM/HTTP del formulario, cliente y contenedor; lint, formato, compilación estricta y build aprobados (transferencia inicial ~90.36 kB, MapLibre diferido). No acredita recorrido con sesión frontend P2 ni CI remoto. La entrega previa de prioridad conserva su evidencia histórica de 939/325.

La primera corrida en el target compartido pasó 1041 pruebas, pero JaCoCo encontró clases recompiladas distintas de las ejecutadas. La verificación final se realizó en una copia temporal de las mismas fuentes sin interferencia del IDE, con JDK 25 y Docker; no se editó target ni se redujeron umbrales. Los reportes de esta ejecución están en `/tmp/p8-programacion-verificacion/target`, fuera de Git; al ejecutar normalmente el comando siguiente se generan en el target del repositorio. El fixture de formulario se ajustó a VARCHAR(20) y el DTO corrigió la aceptación silenciosa de fecha con hora; las pruebas nuevas de ambos casos quedan preservadas.

Desde este repositorio, con el JDK 25 de la base y Docker disponible:

```sh
./mvnw -B -ntp clean spotless:check verify pmd:check
```

Los reportes están en `target/surefire-reports` y `target/site/jacoco/index.html`. Las pruebas de integración reutilizan PostgreSQL/PostGIS y MinIO de `PruebaIntegracion`, sin H2. No se deben editar archivos generados de `target/`.

Los checkouts revisados para esta entrega son backend `2c7e77d` (P2, incluida autorización real por fila) y frontend `a067dff` (fusión P8 sin nuevos archivos de sesión). Aporta inventario, catálogo INE, CRUD/solicitudes/bajas, actualización derivada por evento, mapa/ficha públicos y uso del componente de estado P8. El record `InspeccionPublicada` no acredita un servicio P5 que lo emita ni la integración de daños P4. El token frontend `ROL_PUENTES` sigue con null por defecto y no sustituye sesión/interceptores/guardias de P2. El backend conserva la autorización de mantenimiento. Esta entrega no cambia workflow, migraciones ni módulos P2/P3/P5/P7/P9; los resultados locales no acreditan CI remoto.


Para cerrar la entrega faltan integración real, pruebas pertinentes de rol/fila, manual de usuario con capturas del recorrido disponible, exportación OpenAPI de entrega y revisión/CI/fusión del trabajo pendiente. El diccionario existente se genera desde migraciones; este documento no modifica el esquema.
