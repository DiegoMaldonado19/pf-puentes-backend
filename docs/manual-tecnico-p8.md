# Manual técnico de P8 — Índice de Condición y mantenimiento

Actualizado: 20/10/2026. Describe el código disponible; la integración completa sigue pendiente. Trazabilidad: RN-IC-01–08, RN-MTO-01–09 y DT-CAL-08. Las HU/documentos personales fuera de este repositorio no acompañan al código cuando el equipo lo descarga; este manual sí puede compartirse mediante el commit/PR.

## 1. Módulos y límites actuales

| Pieza | Disponible | Pendiente |
|---|---|---|
| `indice/` | Núcleo puro, pesos normalizados, repositorios, creación Java autorizada/auditada, tres GET Admin | Adaptación real P4, publicación P5, confirmación/recálculos y creación HTTP |
| `mantenimiento/` | Máquina, origen orden/puente/inspección, consultas de historial/detalle/propuestas, ajuste justificado de prioridad y POST de aceptación/inicio/cierre/descarte y carga/consulta paginada de evidencia P1 | Programación de fecha, ajuste HTTP, cancelación, generación por catálogo y jobs |
| Frontend P8 | Componente de condición, consulta de pesos, revisión/aceptación, bandeja, contenedor HTTP de propuestas e historial/detalle con inicio, evidencia, cierre y descarte opcionales | Sesión/guardias P2, rutas y conexiones pendientes; las piezas no aparecen aún en la página inicial |

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

Propuestas, historial completo y detalle tienen GET para Catedrático y Administrador, con confirmaciones del grupo comunicadas por el usuario. Añade `puente_id` al contrato de página. También se confirmaron los actores de evidencia y cierre y su carga en PROGRAMADA/EN_EJECUCION; están implementados. El grupo confirmó también Catedrático/Admin para descarte, ya implementado. Siguen pendientes creación HTTP de pesos, ajuste de prioridad, cancelación, programación de fecha y recálculo HTTP. El permiso de lectura no asigna actores de escritura. El permiso de aceptación de RN-MTO-02 no se convierte automáticamente en una matriz completa de lectura o ejecución.

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

## 6. Conexiones pendientes del equipo

| Responsable | Entrega necesaria para completar el flujo |
|---|---|
| P4 | JSON Schema real, respuestas de ejemplo, referencias/metadatos de daño, severidades y portantes; acuerdo de versiones con pesos |
| P5 | El record InspeccionPublicada ya llegó con P3; faltan publicación real que calcula/guarda IC/versiones y emisor en esa transacción |
| P3 | Inventario/listado/mapa/ficha y uso de estado P8 ya recibidos; conectar los contenedores P8 al puente seleccionado disponible |
| P2 | Sesión, interceptores y guardias frontend; aplicar los mecanismos backend existentes en cada nueva operación de P8 |
| P1 | Almacenamiento/captura reutilizados para evidencia de órdenes; conservar contrato 16 |
| P9 | Emisor `NotificacionService` ya recibido y verificado; P8 debe integrar jobs con destinatarios/repetición acordados |
| Equipo/catedrático | Actores pendientes de otras operaciones, catálogo daño → intervención, calibración/acta, prioridad, precisión/anulaciones y reglas temporales no especificadas |

El evento y las políticas pendientes son acuerdos por integrar, no clases/rutas ya disponibles. La generación de propuestas, recálculo, confirmación y jobs no se dan por implementados por existir tablas o enum.

## 7. Verificación y entrega

Verificación local del 20/10/2026, paso 5a: **909 pruebas backend** sin fallos/errores/omisiones, incluidas 33 nuevas de descarte. El merge de P3 pasó primero 876; el paso 4 anterior tenía 784. Compilación limpia, formato, PMD, empaquetado y las tres reglas JaCoCo aprobados (servicios ≥70 %, núcleo IC/máquina 100 % según sus clases configuradas). El [registro del merge de Cursos](verificacion-p8-post-merge-2026-10-20.md) conserva sus 651 casos históricos. **280 pruebas frontend** (38 nuevas de descarte, sobre las 242 tras P3), lint, formato, compilación estricta y build aprobados; no se acredita recorrido con sesión P2 ni CI remoto.

Desde este repositorio, con el JDK 25 de la base y Docker disponible:

```sh
./mvnw -B -ntp clean spotless:check verify pmd:check
```

Los reportes están en `target/surefire-reports` y `target/site/jacoco/index.html`. Las pruebas de integración reutilizan PostgreSQL/PostGIS y MinIO de `PruebaIntegracion`, sin H2. No se deben editar archivos generados de `target/`.

La última fusión local verificada es backend `6915578` (P3, develop `adddcca`) y frontend `514c884`. Aporta inventario, catálogo INE, CRUD/solicitudes/bajas, actualización derivada por evento, mapa/ficha públicos y uso del componente de estado P8. El record `InspeccionPublicada` no acredita un servicio P5 que lo emita ni la integración de daños P4. El token frontend `ROL_PUENTES` sigue con null por defecto y no sustituye sesión/interceptores/guardias de P2. El backend conserva la autorización de mantenimiento. Esta entrega no cambia workflow, migraciones ni módulos P3/P5/P7/P9; los resultados locales no acreditan CI remoto.


Para cerrar la entrega faltan integración real, pruebas pertinentes de rol/fila, manual de usuario con capturas del recorrido disponible, exportación OpenAPI de entrega y revisión/CI/fusión del trabajo pendiente. El diccionario existente se genera desde migraciones; este documento no modifica el esquema.
