# Manual técnico de P8 — Índice de Condición y mantenimiento

Actualizado: 20/10/2026. Describe el código disponible; la integración completa sigue pendiente. Trazabilidad: RN-IC-01–08, RN-MTO-01–09 y DT-CAL-08. Las HU/documentos personales fuera de este repositorio no acompañan al código cuando el equipo lo descarga; este manual sí puede compartirse mediante el commit/PR.

## 1. Módulos y límites actuales

| Pieza | Disponible | Pendiente |
|---|---|---|
| `indice/` | Núcleo puro, pesos normalizados, repositorios, creación Java autorizada/auditada, tres GET Admin | Adaptación real P4, publicación P5, confirmación/recálculos y creación HTTP |
| `mantenimiento/` | Máquina, origen orden/puente/inspección, consultas internas, ajuste justificado de prioridad y GET de propuestas/POST de aceptación | Programación de fecha, demás operaciones HTTP, evidencia integrada, generación por catálogo y jobs |
| Frontend P8 | Componente de condición, consulta de pesos, revisión/aceptación, bandeja y contenedor HTTP de consulta/recarga | Sesión/guardias P2, rutas y conexiones pendientes; las piezas no aparecen aún en la página inicial |

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
| `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/propuestas` | CATEDRATICO o ADMINISTRADOR | Página de PROPUESTA del puente por ID descendente, con `puente_id` también si está vacía |
| `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/aceptacion` | CATEDRATICO o ADMINISTRADOR | DTO de la orden aceptada, estado PROGRAMADA |

Los GET paginados aceptan `page` desde 0 y `size` entre 1 y 100; valores predeterminados 0/20. La salida tiene `content`, `page`, `size`, `total_elements`, `total_pages`. Una colección existente sin registros devuelve página vacía; recurso inexistente, 404.

La aceptación usa **Body: none**. Requiere una PROPUESTA guardada del puente indicado; conserva fecha programada e intervención. No crea propuestas ni permite elegir un estado arbitrario. Aceptar una orden sin fecha no acredita la programación rutinaria anual. Pueden verificarse el estado retornado y el rechazo 409 de un segundo intento; el segundo intento no duplica auditoría.

Errores en RFC 7807, con `type`, `title`, `status`, `detail` e `instance`:

| Código | Significado en estas operaciones |
|---|---|
| 400 | UUID o parámetros de página inválidos |
| 401 | Sin sesión o token inválido |
| 403 | Rol sin permiso |
| 404 | Formulario/juego/puente inexistente, u orden inexistente en el puente solicitado |
| 409 | La orden ya no está en PROPUESTA |
| 422 | En aceptación, inspección de origen incompatible con el puente |

La consulta de propuestas ya tiene GET para Catedrático y Administrador, según confirmación del grupo comunicada por el usuario. Añade `puente_id` al contrato de página. No existen aún GET de historial completo, creación HTTP de pesos, cierre, subida de evidencia desde P8 o recálculo HTTP. El permiso de aceptación de RN-MTO-02 no se convierte automáticamente en una matriz completa de lectura o ejecución.

Detalle: [ADR 0020](adr/0020-consulta-paginada-de-juegos-de-pesos.md), [ADR 0025](adr/0025-aceptacion-autorizada-y-auditada-de-propuestas.md).

## 5. Órdenes, transacciones y evidencia

La máquina interna admite PROPUESTA → PROGRAMADA, PROPUESTA → DESCARTADA, PROGRAMADA → EN_EJECUCION y EN_EJECUCION → EJECUTADA. CANCELADA puede leerse como histórico, pero su origen sigue pendiente y ninguna transición hacia ella está habilitada. `OrdenMantenimiento.cambiarEstado` valida antes de mutar; no existe setter público de estado.

Cerrar exige fecha, responsable no vacío y una foto guardada. El contador de `DatosTransicionOrden` es interno: la futura fachada debe consultarlo mediante `ArchivoService.contarEvidencias(ordenId)`, nunca confiar en un contador del cliente. P1 ya ofrece `guardarEvidencia`, `listarEvidencias` y `contarEvidencias`; P8 aún debe conectar autorización, estado, subida y cierre. Una foto seleccionada cuya carga no terminó no satisface el mínimo.

`OrdenMantenimientoService.aceptar` bloquea por orden y puente antes de llamar al bean auditado. Así el aspecto de P2 toma una instantánea anterior coherente con el bloqueo; cambio y auditoría se confirman o revierten juntos. Dos aceptaciones concurrentes producen una aceptación y un 409. No se escriben auditorías directamente desde P8.

`ConsultaOrdenes` es interno y devuelve DTO en transacción de solo lectura. `listarPropuestas` filtra PROPUESTA en base de datos, incluidos sus totales; el historial completo sigue disponible internamente. El repositorio no expone borrado físico. La inspección de origen es opcional; si existe, el validador comprueba que corresponda al mismo puente.

`ajustarPrioridad` conserva la sugerida y exige justificación no blanca para cambiar la vigente; no implementa la fórmula de sugerencia IC/peso ni tiene endpoint. Los roles de inicio/cierre, otros ajustes y las reglas temporales se mantienen pendientes donde el enunciado no los especifica.

Detalle: [ADR 0021](adr/0021-nucleo-de-transiciones-de-ordenes.md), [ADR 0022](adr/0022-persistencia-y-consulta-base-de-ordenes.md), [ADR 0023](adr/0023-validacion-del-origen-de-ordenes.md), [ADR 0024](adr/0024-ajuste-justificado-de-prioridad-de-ordenes.md).

## 6. Conexiones pendientes del equipo

| Responsable | Entrega necesaria para completar el flujo |
|---|---|
| P4 | JSON Schema real, respuestas de ejemplo, referencias/metadatos de daño, severidades y portantes; acuerdo de versiones con pesos |
| P5 | Publicación que invoca el IC, guarda su resultado/versiones y emite el evento acordado en la misma transacción |
| P3 | Integrar estado/Sin evaluar en lista, ficha y mapa mediante datos de inventario |
| P2 | Sesión, interceptores y guardias frontend; aplicar los mecanismos backend existentes en cada nueva operación de P8 |
| P1 | Reutilizar almacenamiento/captura existentes al conectar evidencia de órdenes |
| P9 | Servicio real de notificaciones para alertas con política de destinatarios y repetición acordada |
| Equipo/catedrático | Actores pendientes de otras operaciones, catálogo daño → intervención, calibración/acta, prioridad, precisión/anulaciones y reglas temporales no especificadas |

El evento y las políticas pendientes son acuerdos por integrar, no clases/rutas ya disponibles. La generación de propuestas, recálculo, confirmación y jobs no se dan por implementados por existir tablas o enum.

## 7. Verificación y entrega

Verificación local del 20/10/2026: **651 pruebas backend** sin fallos/errores/omisiones; compilación limpia, Spotless, PMD y las tres reglas JaCoCo aprobados. Cobertura exigida: servicios ≥70 % como configuración del proyecto; núcleo IC y máquina 100 % por sus reglas específicas. Frontend: **115 unitarios**, lint, Prettier, build y compilación estricta de fuentes/pruebas/plantillas aprobados. La comprobación posterior al merge incluye 27 pruebas de Cursos y se detalla en [verificación del 20/10](verificacion-p8-post-merge-2026-10-20.md). Los 115 unitarios y controles frontend corresponden a la entrega anterior; no se repitieron en este paso documental. Las pruebas DOM/HTTP no acreditan el recorrido autenticado pendiente de P2.

Desde este repositorio, con el JDK 25 de la base y Docker disponible:

```sh
./mvnw -B -ntp clean spotless:check verify pmd:check
```

Los reportes están en `target/surefire-reports` y `target/site/jacoco/index.html`. Las pruebas de integración reutilizan PostgreSQL/PostGIS y MinIO de `PruebaIntegracion`, sin H2. No se deben editar archivos generados de `target/`.

La última fusión local verificada es `1f801da`: incorpora Cursos de P7 (`875a391`) y conserva el GET de propuestas de P8. La corrección previa de P1 (`0fbe842`) descarga por `GITHUB_SHA` en el deploy. Este merge no incorpora los contratos de P4/P5 ni la sesión frontend de P2. La conexión de propuestas se documenta en [ADR 0026](adr/0026-consulta-autorizada-de-propuestas.md); las verificaciones locales no acreditan CI remoto.

Para cerrar la entrega faltan integración real, pruebas pertinentes de rol/fila, manual de usuario con capturas del recorrido disponible, exportación OpenAPI de entrega y revisión/CI/fusión del trabajo pendiente. El diccionario existente se genera desde migraciones; este documento no modifica el esquema.
