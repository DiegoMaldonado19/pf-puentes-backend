# pf-puentes-backend

### Generación automática de propuestas de mantenimiento por daño, detector crítico P4 y fachada oficial de IC (P8, 25/10/2026)

1. **`DetectorCondicionesCriticas` (`RN-IC-05`):** evalúa automáticamente los 6 campos con marcador `"x-anulacion-critica"` del esquema P4 (`SOCAVACION`, `ASENTAMIENTO`, `PERDIDA_SECCION`, `AUSENCIA_PORTANTE`) con umbral configurable por defecto `>= 3` y se conecta también a `EstimacionPreliminarIcService` cuando no se envían banderas explícitas.
2. **`CalculoIndiceCondicionService` (`Contrato 3` para P5):** fachada transaccional de solo lectura `calcular(datos, formularioId[, versionPesosId])` que selecciona el juego de pesos vigente, prepara las calificaciones con `PreparacionCalificacionesService`, detecta anulaciones críticas y devuelve `ResultadoCalculoOficialIc`.
3. **`CatalogoIntervencionesDano`, `GeneracionPropuestasMantenimientoService` y `PropuestasInspeccionPublicadaListener` (`HU-P8-MTO-02`, `RN-MTO-02/06/07`, `Contrato 6`):** al escuchar `InspeccionPublicada` en la misma transacción (`MANDATORY`), evalúa cada elemento dañado (`2 -> PREVENTIVO`, `3 -> CORRECTIVO`, `4` o anulación crítica `-> EMERGENCIA`), calcula `prioridad_sugerida` (`ALTA`, `MEDIA`, `BAJA`) según IC y peso del elemento, y crea órdenes en estado `PROPUESTA` auditadas con `@Auditable` de forma idempotente por `(inspeccion_id, elemento_ref)`. Incluye además `asegurarPropuestaRutinariaAnual(puenteId)` para `RN-MTO-07`. [ADR 0045](docs/adr/0045-generacion-de-propuestas-por-dano-y-deteccion-de-anulaciones.md) y [manual técnico](docs/manual-tecnico-p8.md).

### Estimación preliminar de IC y validación de confirmación con justificación (P8, 25/10/2026)

`POST /api/v1/formularios/{formularioId}/ic-preliminar` y `POST /api/v1/inspecciones/{inspeccionId}/ic-preliminar` permiten estimar dinámicamente el Índice de Condición sobre borradores de formularios o inspecciones sin persistir resultados ni mutar la condición vigente del puente en el inventario (`RN-IC-07`). Devuelve `EstimacionPreliminarIcDTO` con bandera `preliminar: true` y selección automática del juego de pesos vigente. Además, `POST /api/v1/inspecciones/confirmacion-estado/validacion` valida la concordancia entre `estado_calculado` y `estado_confirmado`, exigiendo justificación explicativa no vacía (mínimo 10 caracteres) únicamente ante discrepancia (`RN-IC-06`, `ConfirmacionEstadoCondicion`). [ADR 0044](docs/adr/0044-estimacion-preliminar-del-ic-y-confirmacion-con-justificacion.md) y [manual técnico](docs/manual-tecnico-p8.md).

### Cancelar órdenes programadas con motivo (P8, 25/10/2026)

`POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/cancelacion`, solo Catedrático y Administrador (política adoptada por el responsable P8 para la transición hacia `CANCELADA` de `RN-MTO-04/09`): recibe JSON `{"motivo":"..."}` y mueve exclusivamente `PROGRAMADA → CANCELADA` conservando motivo, intervención, prioridades, fecha programada y evidencias previas en modo solo lectura. Unifica el servicio transaccional y auditado `DecisionConMotivoOrdenService` con el descarte de propuestas. Estados distintos o repetición responden `409` sin duplicar auditoría; motivo ausente o en blanco responde `400`. [ADR 0042](docs/adr/0042-cancelacion-auditada-de-ordenes-programadas.md) y [manual técnico](docs/manual-tecnico-p8.md).

### Preparar calificaciones desde respuestas reales (P8, 25/10/2026)

`PreparacionCalificacionesService.preparar(formularioId, juegoId, datos)` es una operación interna de solo lectura que entrega entradas inmutables para la calculadora IC existente. Reutiliza el extractor P4, los repositorios, MapStruct y las validaciones del dominio. Lee cada daño por su ruta real, comprueba versión/cobertura completa del juego indicado y obtiene todos los pesos. Ausentes, null, no aplicables y daños repetidos sin contrato producen error; cero sigue siendo válido. No interpreta condiciones críticas ni produce un IC oficial. [Contrato y límites: ADR 0041](docs/adr/0041-preparacion-de-calificaciones-desde-respuestas-reales.md), [manual técnico](docs/manual-tecnico-p8.md).

No hay nueva pantalla o API: el siguiente consumidor será el flujo de inspección P5, que aún debe aportar autorización, validación completa P4 y publicación. La creación completa de pesos ya está implementada; las menciones anteriores a su ausencia son históricas.

**Verificación final 25/10/2026:** `clean spotless:check verify pmd:check` aprobado en `/tmp/p8-respuestas-cjgx_cm7`, con Java 25 y PostgreSQL/PostGIS/MinIO reales: **1259 pruebas**, 35 nuevas, cero fallos/errores/omisiones. Adaptador y servicio nuevos: 100 % de líneas y ramas; extractor/lector de metadatos también 100 %. Pasaron empaquetado y las tres reglas JaCoCo. Fuentes/recursos y pom idénticos al repositorio; diccionario generado idéntico al versionado. Las corridas focalizadas anteriores aprobaron 57 casos unitarios y 35 de integración. Sin cambios frontend ni nueva verificación de navegador, al no haber cambio HTTP/UI; no acredita CI remoto. No se usó `.env` ni la base local.

### Crear juegos completos de pesos (P8, 25/10/2026)

`POST /api/v1/formularios/{formularioId}/juegos-pesos`, solo Administrador: `{"acta":null,"pesos":[{"elemento_ref":"referencia-real","peso":1.25}]}`. Debe incluir exactamente un peso por cada campo de daño obtenido del servidor. Devuelve 201, DTO y Location del juego nuevo; 400/401/403/404/422 documentados. Transacción, bloqueo compartido del formulario y auditoría por fila reutilizada de P2. No modifica juegos anteriores ni inspecciones, no elige un juego oficial ni recalcula. El ejemplo ilustra el formato, no valores calibrados ni un juego completo de V1.0.0.

La política de completitud y creación para borradores/activas/retiradas fue **adoptada con autorización del responsable P8**, sin atribuirla al texto literal ni a una confirmación del grupo. Queda documentada para el equipo en [ADR 0040](docs/adr/0040-creacion-completa-y-versionada-de-pesos.md) y [manual técnico](docs/manual-tecnico-p8.md). Acta null significa provisional; una referencia no certifica firma/calibración. Se superan las menciones anteriores a esperar esa decisión para habilitar el guardado.

Verificación de creación: **1224 pruebas backend aprobadas**, 21 nuevas; Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados. Fachada nueva: 100 % de líneas y ramas. Frontend: 595 pruebas y controles aprobados según su README. Diccionario sin migraciones; no acredita CI remoto.

**Siete escenarios Playwright y un orquestador JUnit aprobados**, con build de producción, Chrome y API/BD/MinIO reales. Creación de dos juegos de 19 pesos y verificación SQL de históricos, esquema, inspecciones y auditoría. [Guía reproducible](docs/prueba-navegador-p8.md).

### Consulta de campos P4 desde pesos (P8, 24/10/2026)

`GET /api/v1/formularios/{formularioId}/elementos-dano?page=0&size=20` devuelve una página de referencias canónicas ordenadas del esquema persistido. Solo Administrador en HTTP y servicio; tamaño máximo 100, errores 400/401/403/404/422. Funciona aunque la versión no tenga pesos; no crea configuraciones. Frontend: `/admin/pesos` → **Consultar campos de daño del formulario** → seleccionar versión. Reutiliza el listado P4 y mantiene separado el catálogo de versiones con juegos guardados. [ADR 0039](docs/adr/0039-consulta-administrativa-de-referencias-de-dano.md).

El usuario decidió consultar al grupo la completitud del juego y la creación para versiones publicadas antes de habilitar el guardado; selección/publicación P5 y calibración siguen pendientes.

Verificación 25/10/2026: **1203 pruebas backend aprobadas**, diez nuevas; Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados. Servicio nuevo: 100 % de líneas y ramas. Diccionario generado sin diferencias. Frontend: 568 pruebas aprobadas y controles documentados en su README; no acredita CI remoto.

Recorrido final: **seis escenarios Playwright y un orquestador JUnit aprobados**, con Chrome/build de producción y backend/BD/MinIO reales. Incluye la consulta de los 19 campos de la semilla P4 sin juegos de pesos. [Guía actualizada](docs/prueba-navegador-p8.md).

### Metadatos P4 para pesos de P8 (24/10/2026)

`MetadatosDanoFormulario.obtener(UUID)` lee el esquema persistido de esa versión y devuelve `ElementosDeDano` con las referencias `x-elemento-ref` de los campos `x-dano: true`. La versión V1.0.0 contiene 19 referencias; la ruta del dato puede ser diferente de la referencia del peso. Valida escala integer 0–4 y referencias únicas; rechaza metadatos incompatibles con 422 y versiones inexistentes con 404. Es lectura interna sin caché, también para históricos, sin nueva ruta HTTP. [Contrato, límites y pruebas: ADR 0038](docs/adr/0038-metadatos-de-dano-desde-el-formulario-persistido.md).

Este adaptador prepara la creación de pesos con metadatos obtenidos en servidor. La creación HTTP/pantalla, la lectura de respuestas y las condiciones críticas reales siguen pendientes; no se deducen umbrales del marcador crítico de P4.

Verificación local de este merge: **1193 pruebas backend**, 36 nuevas; Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados. Adaptador/extractor: 100 % de líneas y ramas; diccionario generado sin diferencias. Frontend: 553 pruebas y controles de formato, lint, TypeScript y build aprobados. No acredita CI remoto.

### Recorrido P8 con navegador y API reales (23/10/2026)

`./mvnw -B -ntp -Dtest=RecorridoP8Navegador test` inicia PostgreSQL/PostGIS y MinIO temporales y ejecuta Playwright contra la SPA y Spring Boot reales: login, pesos, mantenimiento, foto/cierre, descarte y permisos. **Cinco escenarios de navegador y un orquestador JUnit aprobados**. No usa `.env` ni la base local, no agrega datos a producción y no sustituye la publicación P4/P5. [Preparación, comando y resultados](docs/prueba-navegador-p8.md). Este ejecutor se invoca explícitamente y no exige navegador al CI backend normal.

### Consulta administrativa de pesos (P8, 23/10/2026)

`GET /api/v1/juegos-pesos/formularios?page=0&size=20` devuelve referencias `id`/`codigo` de versiones con juegos guardados, sin duplicados, ordenadas por código e ID y con máximo 100 filas. Solo Administrador, tanto en controlador como servicio; 400 por paginación inválida/desplazamiento excesivo y 401/403 por acceso. Sin versiones con pesos, devuelve página vacía. No expone esquema/mapeo ni certifica calibración. [ADR 0037](docs/adr/0037-catalogo-administrativo-de-formularios-con-pesos.md).

Frontend `/admin/pesos` usa la sesión/interceptores/guardia ya recibidos de P2: elegir código de versión → juego → elementos. La creación HTTP sigue pendiente; los metadatos de referencias P4 ya se extraen en servidor desde el incremento del 24/10. Mantenimiento también tiene acceso protegido desde ficha P3. Las menciones históricas a esperar otra entrega de autenticación P2 quedan superadas para esas consultas.

Verificación local: **1134 pruebas backend**, siete escenarios nuevos, sin fallos/errores/omisiones; compilación limpia, Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados. Diccionario regenerado idéntico al versionado. Sin migraciones ni cambios del workflow. Frontend: 495 pruebas y controles aprobados, según su README. No acredita CI remoto ni navegador con backend real.

### Seguimiento rutinario anual (P8, 21/10/2026)

El proceso diario de las 06:15 America/Guatemala usa @Scheduled/ShedLock y recorre lotes de 100. Cuando falta una rutina PROGRAMADA/EN_EJECUCION fechada entre hoy y el límite anual, avisa por P9 a Catedráticos y Administradores activos, incluso antes del vencimiento. Un aviso por puente, destinatario y fecha de referencia; lectura sin reenvío, nuevo período tras nueva ejecución rutinaria. Esta política fue aprobada por el usuario para los detalles no expresos del enunciado. Revalida bajo bloqueo y confirma notificación/marca en la misma transacción; un fallo se reintenta sin repetir envíos confirmados. [ADR 0036](docs/adr/0036-recordatorios-de-programacion-rutinaria-anual.md). El recordatorio no crea ni acepta órdenes y no acredita por sí solo programación efectiva anual.

El diccionario generado incorpora `aviso_inspeccion_vencida` y `aviso_rutinario_anual`: corrige la omisión que hacía fallar la comprobación del PR. Si se verifica en una copia temporal, trasladar `docs/diccionario-de-datos.md` generado al repositorio y comprobar que una nueva generación lo conserva idéntico, además de aprobar Maven.

Verificación local: **1127 pruebas backend**, 17 nuevas, sin fallos/errores/omisiones; compilación limpia, Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados. Servicio de recordatorios con 100 % de líneas y ramas. Segunda generación focalizada del diccionario aprobada y contenido idéntico al incluido en el repositorio. Frontend sin cambios; presentación P9 e integración de acceso P2 siguen pendientes.

`GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/rutinario-anual?page=0&size=20` entrega a Catedrático/Admin la referencia (última ejecución rutinaria o registro sin ejecuciones), límite de un año y página de rutinarias PROGRAMADA/EN_EJECUCION. Base confirmada por el grupo según reporte del usuario; roles/criterio de consulta adoptados por delegación del usuario para los detalles no precisados en RN-MTO-07.

Conteos globales de fechas dentro de la ventana inclusiva hoy–límite, ausencia de fecha, fechas pasadas y posteriores al límite. Los dos últimos indicadores pueden coincidir: no se suman. No se infiere ejecución ni se crean órdenes/avisos desde la consulta. Máximo 100 por página, 400/401/403/404 documentados. [ADR 0035](docs/adr/0035-consulta-del-seguimiento-rutinario-anual.md) y [manual con prueba Postman](docs/manual-tecnico-p8.md).

Verificación: **1105 pruebas backend aprobadas**, 16 nuevas, PostgreSQL/reloj controlado. Pasan Spotless, PMD, empaquetado y tres reglas JaCoCo (servicios 95.28 %; IC/máquina 100 % según clases configuradas). Automatización rutinaria anual, acceso frontend integrado y revisión/entrega pendientes.

API REST del Sistema de Gestión de Puentes (SGP).

## Estado (20/10/2026)

| Paquete | Qué hay | Responsable |
|---|---|---|
| `common/` | Errores RFC 7807 (`NegocioException`), CORS, OpenAPI en `/api/docs`, ShedLock | P1 |
| `common/auditoria/` | Bitácora automática con `@Auditable`: valores anterior y posterior, saneamiento, IP y correlación | P2 |
| `common/seguridad/` | Seguridad stateless con JWT de acceso (15 min), rutas públicas/privadas, BCrypt 12 y 401/403 en RFC 7807 | P2 |
| `archivo/` | Subida de fotos y PDF, borrado en borradores, URL firmadas y purga de 210 días (RN-ARC) | P1 |
| `indice/` | Fórmula, clasificación y cuatro anulaciones con banderas normalizadas; pruebas propias, adaptación e integración pendientes ([ADR 0016](docs/adr/0016-nucleo-puro-del-indice-de-condicion.md)) | P8 |
| Configuración de pesos (`indice/`) | Modelo, repositorios, creación interna transaccional auditada y API de consulta paginada para Admin; adaptador P4, API de creación y pantalla pendientes ([ADR 0027](docs/adr/0027-validacion-de-configuraciones-de-pesos.md), [ADR 0019](docs/adr/0019-creacion-transaccional-y-auditada-de-pesos.md), [ADR 0020](docs/adr/0020-consulta-paginada-de-juegos-de-pesos.md)) | P8 |
| `mantenimiento/` | Núcleo de transiciones/cierre/descarte con cobertura 100 %; repositorio interno de guardado y consulta paginada sin métodos de borrado; validador de existencia y correspondencia inspección/puente; DTO de lectura con MapStruct y consultas internas paginadas/detalle por puente, incluida consulta de propuestas pendientes filtrada en base de datos; ajuste de prioridad con justificación, conservando la sugerida; servicio/PATCH autorizado y auditado en estados abiertos. GET paginados de historial/propuestas y detalle de órdenes para Catedrático/Admin; servicios/POST de aceptación, inicio, cierre y descarte auditados con bloqueo concurrente; carga y consulta paginada de evidencia P1 para Catedrático/Admin; programación de fecha y alerta diaria de inspección vencida mediante P9; otras operaciones y acceso UI pendientes ([ADR 0021](docs/adr/0021-nucleo-de-transiciones-de-ordenes.md), [ADR 0022](docs/adr/0022-persistencia-y-consulta-base-de-ordenes.md), [ADR 0023](docs/adr/0023-validacion-del-origen-de-ordenes.md), [ADR 0024](docs/adr/0024-ajuste-justificado-de-prioridad-de-ordenes.md), [ADR 0025](docs/adr/0025-aceptacion-autorizada-y-auditada-de-propuestas.md)) | P8 |
| Los demás módulos | Tabla, migración y entidad del modelo base ([ADR 0010](docs/adr/0010-modelo-de-datos-base.md)); todavía sin servicios ni endpoints | Cada responsable |

- La seguridad base (cadena de filtros JWT, `@EnableMethodSecurity`) y el inicio de sesión, la renovación, el cierre de sesión y el perfil propio ya existen. Ver [Seguridad](#seguridad) e [Inicio de sesión](#inicio-de-sesión-y-bloqueo-rn-usr-07).
- Los contratos entre módulos y las fechas están en `docs-analisis/Equipo`.
- Integración y prueba de lo implementado por P8: [Manual técnico de IC y mantenimiento](docs/manual-tecnico-p8.md), con API disponible, contratos Java, transacciones y dependencias pendientes.
- [Verificación de P8 tras integrar Cursos, 20/10](docs/verificacion-p8-post-merge-2026-10-20.md): 651 pruebas backend y controles aprobados; colisión de ADR resuelta.

### Aceptar una propuesta de mantenimiento (P8)

`POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/aceptacion`

- Autenticación JWT de una cuenta activa con rol **CATEDRATICO** o **ADMINISTRADOR** (RN-MTO-02).
- Sin cuerpo. Requiere una orden persistida en PROPUESTA del puente indicado; devuelve 200 con el DTO de la orden en PROGRAMADA. Conserva fecha programada, prioridades e intervención.
- Errores: 400 para UUID inválido, 401 sin sesión, 403 por rol, 404 si no existe en ese puente, 409 si ya no es PROPUESTA y 422 por inspección de origen incompatible.
- El cambio y su auditoría se confirman juntos. Dos aceptaciones concurrentes producen una sola transición; la segunda recibe 409.
- No genera propuestas de demostración. La creación desde daños depende del catálogo y de P5. La fecha se registra/cambia mediante el PATCH documentado al final; cancelación, propuestas automáticas y garantía rutinaria anual siguen pendientes; la alerta de inspección vencida ya está implementada; aceptar no acredita por sí solo periodicidad anual.

Para probarlo en Postman, usa POST a esa ruta con IDs existentes y `Authorization: Bearer <token de acceso>`; deja Body en `none`. El contrato está también en Swagger. Detalles de integración: [ADR 0025](docs/adr/0025-aceptacion-autorizada-y-auditada-de-propuestas.md).

### Guardar un borrador de inspección con clave idempotente (G7.2)

`PUT /api/v1/inspecciones/{id}` requiere una cuenta activa con rol **ESTUDIANTE**, **PROFESIONAL_EXTERNO** o **CATEDRATICO**, titular de la inspección. `Idempotency-Key` es opcional; ausente o en blanco conserva el flujo anterior. Una clave no blanca se recorta y admite hasta 100 caracteres Unicode. `X-Sgp-Sync` es opcional, predeterminado `false`; con `true` se fusiona una versión local desactualizada y se registra la copia de conflicto.

Con clave, una repetición de la misma solicitud reproduce status y bytes de respuesta. La huella v1 considera método, ruta con UUID canónico, `X-Sgp-Sync` y DTO completo. La operación y el resultado se guardan en una transacción PostgreSQL. La titularidad se valida antes de replay; una operación nueva vuelve a validar que el borrador sea editable.

- **Respuestas reproducibles:** 200; rechazos terminales por payload 413 `cuerpo-demasiado-grande` y 422 por `campo-inmutable`, `grupo-con-danos`, `datos-invalidos` o `fecha-inspeccion-invalida`.
- **No se cachean:** 400 (clave mayor de 100 caracteres: `idempotencia-invalida`), 401, 403, 404 (`inspeccion-no-encontrada`), 409 por `version-desactualizada`, `inspeccion-no-editable`, `clave-idempotencia-incompatible` o `operacion-en-proceso`, y fallos 500. La espera por una clave concurrente vence a los 2 segundos con 409 `operacion-en-proceso`, reintentable con la misma clave.
- Las claves heredadas guardadas solo como JSONB devuelven 409 `clave-idempotencia-incompatible`; no se reinterpretan ni se ejecutan otra vez. El contrato del POST de envío se documenta por separado en la sección siguiente; las otras escrituras no cambian.

El contrato detallado está en la [guía de idempotencia del borrador](docs/idempotencia-borrador.md), el diseño en [ADR 0046](docs/adr/0046-idempotencia-del-put-de-borrador.md) y los encabezados/respuestas en Swagger.

### Enviar una inspección con clave idempotente (G7.2)

`POST /api/v1/inspecciones/{id}/envio` integra el núcleo común: `Idempotency-Key` opcional, recortada y de hasta 100 caracteres Unicode. Sin clave o en blanco conserva el flujo anterior. Autoriza inspección propia/no eliminada antes de replay; solo ejecuciones nuevas exigen editabilidad. La huella SHA-256 v1 incluye método, ruta con UUID canónico, `X-Sgp-Sync` y DTO completo canonicalizado; cuerpo ausente y `{}` se distinguen, fechas generadas por el servidor quedan fuera.

Guarda status/bytes exactos del 200 ENVIADA/PUBLICADA y 422 `inspeccion-no-publicable`, junto con transición, publicación y avisos persistidos en una transacción PostgreSQL. Tras corregir datos, confirmar permanentes o agregar fotos de un rechazo guardado, use una clave nueva. La espera vence a los 2 segundos: 409 `operacion-en-proceso`, reintentable y no cacheable. Legado JSONB u otra huella: 409 `clave-idempotencia-incompatible`, sin conversión ni ejecución.

Autenticación, permisos, inexistencia, estado no editable, binding y fallos transitorios no se guardan. La atomicidad cubre efectos PostgreSQL, no entrega física exactamente una vez de correo/eventos externos. No cambian PUT, otras operaciones ni retención. [Guía de reintentos](docs/idempotencia-envio.md) y [ADR 0047](docs/adr/0047-idempotencia-del-post-de-envio.md).

## Desarrollo local

Requisitos: Docker con Compose 2.32 o superior. En Windows, Docker Desktop con la integración de WSL activada.

```bash
cp .env.local .env   # valores locales compartidos; .env.example documenta los de la EC2
docker compose -f compose.local.yaml up --watch
```

| Servicio | Dirección | Credenciales |
|---|---|---|
| API | http://localhost:8080 | — |
| PostgreSQL | `localhost:5432`, base `puentes` | `puentes_migrador` / `password` · `puentes_app` / `password` |
| Consola de MinIO | http://localhost:9001 | `puentes-local` / `password` |

El backend crea el bucket `archivos` de MinIO al arrancar. Si tu volumen local se creó con otras contraseñas, recréalo: `docker compose -f compose.local.yaml down -v`.

**Documentación de la API:** http://localhost:8080/api/docs/swagger-ui.html (el JSON está en `/api/docs`). Todo endpoint nuevo lleva `@Tag`, `@Operation` y un `@ApiResponse` por código.
- Fuera de producción la documentación es pública; con `APP_ENV=prod` (perfil `prod`) exige un token válido.

**Hot reload:** al guardar en `src/`, la app se recompila y devtools la reinicia (unos 5 s). Si cambias `pom.xml`, se reconstruye la imagen.

**Comandos útiles**
- Salud: `docker compose -f compose.local.yaml exec backend curl -s localhost:8081/actuator/health`. El actuator escucha en el puerto 8081, que no se publica (DT-DEP-10).
- Consola SQL: `docker compose -f compose.local.yaml exec postgres psql -U puentes_migrador -d puentes`
- Apagar: `docker compose -f compose.local.yaml down`. Con `-v` también se borran la base y los archivos.

### Usuarios de base de datos

| Usuario | Lo usa | Permisos |
|---|---|---|
| `puentes_migrador` | Flyway | Dueño del esquema y superusuario, porque `CREATE EXTENSION postgis` lo exige |
| `puentes_app` | La aplicación | Solo `SELECT`, `INSERT`, `UPDATE` y `DELETE`: no puede crear ni alterar tablas |

El script `docker/postgres/01-usuario-app.sh` crea `puentes_app` solo cuando el volumen es nuevo. Si cambias su contraseña en el `.env`, recrea el volumen local (`down -v`) o haz `ALTER ROLE`. Detalle en el [ADR 0007](docs/adr/0007-usuarios-de-base-de-datos.md).

### Modelo de datos

- Cada módulo tiene su migración en `src/main/resources/db/migration` y sus entidades en su paquete ([ADR 0010](docs/adr/0010-modelo-de-datos-base.md)).
- **Nunca edites una migración fusionada:** un cambio de esquema es una migración nueva, con fecha y hora en el nombre.
- La app arranca con `ddl-auto=validate`: si tu entidad no coincide con tu tabla, falla al iniciar y te dice qué columna está mal.
- Las entidades usan Lombok (`@Getter`, `@Setter`, constructor protegido), nunca `@Data` ([ADR 0012](docs/adr/0012-lombok-en-las-entidades.md)).
- Toda tabla y columna lleva `COMMENT ON`. Con ellos, `./mvnw verify` regenera el [diccionario de datos](docs/diccionario-de-datos.md): versiónalo con tu migración, porque el CI falla si quedó desactualizado.

### Seguridad

- **Todo es privado por defecto.** Sin `Authorization: Bearer <jwt>` válido, la respuesta es 401 (Problem Details y `WWW-Authenticate: Bearer`); con un rol sin permiso, 403.
- **Token de acceso:** HS256, 15 minutos, solo `sub` (id del usuario), `rol`, `iat` y `exp`. La clave sale de `JWT_SECRET` (mínimo 32 bytes: `openssl rand -hex 32`); si falta o es corta, la app no arranca.
- **El filtro consulta la cuenta en cada petición** (una consulta por clave primaria): solo una cuenta `ACTIVO` pasa, y el rol que autoriza es el de la base. Una baja surte efecto de inmediato.
- **En tus servicios:** `Principal.getName()` es el UUID del usuario y las autoridades son `ROLE_<ROL>`; protege cada método con `@PreAuthorize("hasAnyRole(...)")`.
- **Rutas públicas:** se declaran solo en `sgp.seguridad.rutas-publicas` de `application.properties`, separadas por coma: `/ruta/**` (cualquier método) o `GET /ruta/**`. No hace falta tocar la cadena de filtros.
- **Contraseñas:** inyecta `PasswordEncoder` (BCrypt, factor 12).
- **Healthcheck:** `/actuator/health` (puerto 8081) es la única ruta de actuator sin token.

### Administrador inicial (RN-USR-03)

Al arrancar, si no existe ninguna cuenta con rol `ADMINISTRADOR`, la app crea una `ACTIVO` con el correo verificado a partir de `ADMIN_INICIAL_CORREO`, `ADMIN_INICIAL_NOMBRE` y `ADMIN_INICIAL_CONTRASENA` (BCrypt 12; la creación queda auditada como acción del Sistema).

- **Idempotente:** si ya hay un administrador no hace nada y nunca toca su contraseña; los cambios de contraseña son de US-P2-11.
- **Sin variables:** avisa con un `WARN` en el log y sigue arrancando; sin administrador no se puede invitar a nadie.
- **Contraseña débil** (menos de 10 caracteres, sin letra o sin dígito, RN-USR-06): el arranque falla con un mensaje que indica la política, sin mostrar el valor.
- En local, `.env.local` trae un administrador de desarrollo (`admin@puentes.local`). En dev, stage y prod defínelas como secretos del Environment ([ADR 0004](docs/adr/0004-configuracion-por-entorno-con-github-environments.md)); son opcionales una vez creada la cuenta.

### Inicio de sesión y bloqueo (RN-USR-07)

- `POST /api/v1/auth/login` (público) con `{"correo", "contrasena"}` responde 200 con `access_token` (JWT de 15 minutos), `token_type` (`Bearer`), `expires_in` (segundos) y `usuario` (`id`, `nombre`, `rol`). El token de renovación **no** va en el cuerpo: viaja en la cookie `refresh_token` (`HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, 7 días y `Secure` salvo con `COOKIE_SECURE=false`, solo para desarrollo local). La base guarda su SHA-256 en `token_renovacion`; cada inicio de sesión crea uno nuevo.
- **Errores:** 401 `credenciales-invalidas` (mismo cuerpo para correo inexistente y contraseña incorrecta; con un correo desconocido también se compara un hash, para que el tiempo no delate la cuenta) y 403 `cuenta-bloqueada` (dice cuántos minutos faltan), `cuenta-pendiente` o `cuenta-inactiva`. Estos tres solo se dicen **con la contraseña correcta**; con una incorrecta siempre es el 401.
- **Bloqueo:** 5 fallos dentro de una ventana de 15 minutos bloquean la cuenta 15 minutos (`sgp.login.intentos-maximos`, `sgp.login.ventana`, `sgp.login.bloqueo`). El conteo vive en `usuario` y lo actualiza una sola sentencia atómica (`UsuarioRepository.registrarFallo`), así que intentos simultáneos no pierden cuentas. Mientras dura el bloqueo, un fallo no cuenta ni lo extiende. Un inicio de sesión correcto reinicia el contador. Solo el fallo que bloquea se audita (`BLOQUEAR_CUENTA`).
- **Renovar y cerrar sesión** (públicos, se autentican con la cookie): `POST /api/v1/auth/renovar` responde 200 con el mismo cuerpo del login (token de acceso nuevo; el de renovación no va ni se rota, [ADR 0017](docs/adr/0017-token-de-renovacion-sin-rotacion.md)). Ante cualquier fallo (sin cookie, vencida, revocada, inexistente o cuenta dada de baja) responde un único 401 `sesion-invalida` y ordena borrar la cookie. `POST /api/v1/auth/cerrar-sesion` revoca solo la sesión de esa cookie, borra la cookie y siempre responde 204. Ninguno de los dos se audita.
- **Revocar todas las sesiones de una cuenta:** `SesionService.revocarTodas(usuarioId)` (para la baja y el cambio de contraseña); devuelve cuántas revocó.
- **Perfil propio:** `GET /api/v1/usuarios/me` (cualquier rol autenticado) devuelve `id`, `nombre`, `correo`, `rol` y `estado`. El id sale del token, nunca de un parámetro.
- Las vigencias salen de `sgp.jwt.vigencia-acceso` y `sgp.jwt.vigencia-renovacion`. Para emitir tokens desde otro módulo usa `TokenService`.

### Registro de estudiantes y correo (RN-USR-01, RN-USR-02)

- **Endpoints públicos** (`/api/v1/auth/`, declarados en `sgp.seguridad.rutas-publicas`): `POST /registro` (201, 409 si el correo ya existe), `POST /verificar-correo` con `{"token": "..."}` (204; 422 `token-invalido` si venció, ya se usó o no existe) y `POST /reenviar-verificacion` con `{"correo": "..."}` (202 siempre). El registro ignora cualquier `rol` del cuerpo: la cuenta nace `ESTUDIANTE` y `PENDIENTE`. Verificar el correo no la activa (US-P2-14).
- **Token:** 256 bits aleatorios, vigencia `sgp.verificacion-correo.vigencia` (24 h); en la base solo queda su SHA-256. El enlace es `FRONTEND_URL/verificar-correo?token=...`.
- **Correo:** `EnviadorDeCorreo` (`common/correo/`) tiene dos implementaciones según `CORREO_MODO`. `smtp` usa `SMTP_HOST`, `SMTP_PORT`, `SMTP_USUARIO`, `SMTP_CLAVE` y `CORREO_REMITENTE`. `consola` (por defecto) imprime el correo, enlace incluido, en el log: es solo para desarrollo y la app no arranca con `APP_ENV=prod`. Se envía de forma asíncrona tras el commit: si el SMTP falla, la cuenta queda creada y se pide el reenvío.
- Otros flujos con correo (invitaciones, recuperación) inyectan `EnviadorDeCorreo`; no uses `JavaMailSender` directamente.

### Auditoría (RN-USR-09)

Toda acción que cree, modifique, cambie de estado o modere datos se registra en `auditoria` con una anotación en el método de **servicio**; no escribas en esa tabla ni inyectes su repositorio. Diseño en el [ADR 0015](docs/adr/0015-auditoria-por-aspecto-en-la-capa-de-servicio.md).

```java
@Transactional
@Auditable(accion = Acciones.DAR_DE_BAJA, entidad = "usuario", entidadId = "#id", motivo = "#motivo")
public void darDeBaja(UUID id, String motivo) { ... }          // antes y después: la fila "usuario" de #id

@Auditable(accion = Acciones.CREAR, entidad = "puente", entidadId = "#result.id")
public Puente crear(CrearPuenteDTO datos) { ... }              // una creación no tiene valor anterior
```

- **`accion`:** una constante de `Acciones` (`CREAR`, `MODIFICAR`, `CAMBIAR_ESTADO`, `DAR_DE_BAJA`, `REACTIVAR`, `MODERAR`, `INVITAR`, `REVOCAR_INVITACION`, `VERIFICAR_COLEGIADO`, `BLOQUEAR_CUENTA`). Si necesitas otra, agrégala a `Acciones.CATALOGO` y avisa al equipo.
- **`entidad`:** el nombre de tu clase de entidad en snake_case (`OrdenMantenimiento` → `"orden_mantenimiento"`).
- **`entidadId`:** SpEL con el UUID de la fila: `#parametro` si llega por argumento (se guarda el valor anterior) o `#result.id` si la crea el método. Por defecto es `#result`.
- **`motivo`:** SpEL opcional con la justificación; obligatorio en bajas, moderación y rechazos (RN-FOR-08, RN-INV-08).
- **`condicion`:** SpEL booleana opcional, evaluada con los parámetros y `#result` después de ejecutar el método. Si da falso el método se confirma pero no queda fila (por ejemplo, `condicion = "#result.bloqueoNuevo()"` en `ControlDeIntentos.registrarFallo`: solo el fallo que bloquea se audita).
- **Si el método falla no queda fila; si falla la bitácora se revierte todo** (500 genérico). Un `@Auditable` mal declarado (acción fuera del catálogo, `"crear"`, `"Puente"`, entidad inexistente) **impide que la aplicación arranque**.
- **Nunca se guardan** campos con `contrasena`, `password`, `hash`, `token` o `secret` en el nombre; los correos salen enmascarados (`a***@dominio.com`).
- Sin sesión, la fila queda sin usuario (acción del Sistema). Varias filas de una misma petición comparten `correlacion_id`.
- Cambia la entidad con JPA y llama al método desde otro bean (el aspecto es un proxy, igual que `@Transactional`). No anotes los intentos de login fallidos: solo el bloqueo (`BLOQUEAR_CUENTA`).
- Ejemplo ejecutable: `usuario/AuditablesDePrueba` y `usuario/AuditoriaPorAspectoTest`.

### Procesos programados

Cada `@Scheduled` lleva `@SchedulerLock(name = "...")`: así una sola réplica lo ejecuta (DT-BE-14). La configuración está en `common/ProcesosProgramadosConfig` y la tabla `shedlock` ya existe ([ADR 0014](docs/adr/0014-tabla-shedlock-con-el-esquema-de-la-libreria.md)).

## Antes de abrir un PR

Son los mismos chequeos que corre el CI:

| Etapa | Comando |
|---|---|
| Formato | `./mvnw spotless:apply` (el CI corre `spotless:check`) |
| Build y análisis estático | `./mvnw -B spotless:check package pmd:check -DskipTests` |
| Pruebas y cobertura | `./mvnw -B verify` |

- PMD usa sus reglas por defecto; cada violación sale en la consola y en `target/pmd.xml`.
- `verify` necesita Docker: Testcontainers levanta PostgreSQL + PostGIS y MinIO reales. Las pruebas de integración extienden `PruebaIntegracion`, que comparte un solo contenedor de cada uno en toda la suite.
- Pruebas de rol por endpoint (DT-CAL-03, contrato 12): en tu `@WebMvcTest` agrega `@Import(SeguridadDePrueba.class)` (seguridad real: `@PreAuthorize`, 401 y 403 en RFC 7807) y autentica cada petición con `.with(como(Rol.CATEDRATICO, usuarioId))` de `AutenticacionDePrueba`. Ejemplo completo con 401, 403 y 2xx por rol en `common/seguridad/EjemploPruebaDeRolTest`.
- El reporte de JaCoCo queda en `target/site/jacoco/index.html`.
- El CI falla si las clases `*Service` bajan de 70 % de líneas cubiertas (DT-CAL-01).
- El núcleo IC (`CalificacionElemento`, `CalculadoraIndiceCondicion`, `CondicionesCriticas` y `ResultadoIndiceCondicion`) exige 100 % de líneas y ramas por clase. Sus pruebas pueden ejecutarse sin Docker; comando en el [ADR 0016](docs/adr/0016-nucleo-puro-del-indice-de-condicion.md).
- `PesoConfigurado` y `JuegoPesos` también exigen 100 % de líneas y ramas; validan configuraciones antes de añadir el servicio de persistencia ([ADR 0027](docs/adr/0027-validacion-de-configuraciones-de-pesos.md)).

## Despliegue

GitHub Actions (`.github/workflows/ci-cd.yml`) corre cuatro etapas: build → test → push → deploy.

| Evento | Entorno | Carpeta en la EC2 |
|---|---|---|
| PR hacia `develop` | dev | `~/puentes/dev/pf-puentes-backend` |
| push a `develop` | stage | `~/puentes/stage/pf-puentes-backend` |
| push a `main` | prod | `~/puentes/prod/pf-puentes-backend` |

- **Imagen:** `<DOCKERHUB_USERNAME>/pf-puentes-backend:<número de build>`, más el tag `:tree-<hash>` que la identifica por contenido.
- **Promoción:** si ese código ya tiene imagen, se promueve la misma sin reconstruirla. Prod solo acepta imágenes que pasaron por stage.
- **Deploy:** el job escribe el `.env` del entorno desde su GitHub Environment y corre `docker compose up --wait`. Si un contenedor no queda sano, el job falla.
- **Acceso:** el backend, postgres y minio no publican puertos. La API se alcanza por el nginx del frontend en `https://pf-puentes.duckdns.org[:puerto]/api`, y las fotos por las URL firmadas de `/archivos/` ([ADR 0013](docs/adr/0013-url-firmadas-de-minio-servidas-por-nginx.md)).
- **Revisar un entorno:** `cd ~/puentes/<entorno>/pf-puentes-backend && docker compose ps`
- **Respaldos:** `scripts/respaldo.sh <entorno>` cada noche desde el cron del host, y `scripts/restaurar.sh` para probarlos ([guía de respaldo y restauración](docs/guia-respaldo-y-restauracion.md)).

Las decisiones están en [docs/adr](docs/adr/). Los secretos y variables por configurar están en el [ADR 0004](docs/adr/0004-configuracion-por-entorno-con-github-environments.md).

## Problemas conocidos

**La app no arranca: `Could not resolve placeholder '...'`**

A tu `.env` le falta una variable que se agregó después de que lo creaste. Copia la línea que falta desde `.env.local`.

**Maven falla en WSL con `bad_record_mac` o `Tag mismatch`**

En algunas instalaciones, la red de WSL corrompe las descargas HTTPS grandes. Dentro de Docker no pasa, así que `compose.local.yaml` funciona igual.

Para Maven en el host, baja las dependencias desde un contenedor y luego usa `./mvnw` normal:

```bash
docker run --rm -u "$(id -u):$(id -g)" -v "$PWD":/app -v "$HOME/.m2":/var/maven/.m2 \
  -e MAVEN_CONFIG=/var/maven/.m2 -w /app maven:3.9.16-eclipse-temurin-25 \
  mvn -B -Duser.home=/var/maven dependency:go-offline
```

## Tecnologías y versiones

**Lenguaje y build**

| Tecnología | Versión |
|---|---|
| Java (Eclipse Temurin) | 25.0.4 |
| Maven (wrapper) | 3.9.16 |

**Framework y dependencias**

| Dependencia | Versión |
|---|---|
| Spring Boot (actuator, data-jpa, flyway, security, validation, webmvc, devtools) | 4.1.1 |
| Spring Framework | 7.0.9 |
| Tomcat embebido | 11.0.24 |
| Hibernate ORM + hibernate-spatial | 7.4.5.Final |
| hypersistence-utils-hibernate-73 | 3.16.0 |
| Jackson | 3.1.5 |
| Flyway (+ flyway-database-postgresql) | 12.4.0 |
| PostgreSQL JDBC | 42.7.13 |
| springdoc-openapi-starter-webmvc-ui | 3.1.0 |
| MapStruct | 1.6.3 |
| JJWT (api, impl, jackson) | 0.13.0 |
| AWS SDK for Java v2 (s3), cliente de MinIO | 2.55.11 |
| ShedLock (spring, provider-jdbc-template) | 7.10.1 |
| Lombok (+ lombok-mapstruct-binding 0.2.0) | 1.18.46 |

**Pruebas y calidad**

| Herramienta | Versión |
|---|---|
| JUnit Jupiter | 6.0.3 |
| Mockito | 5.23.0 |
| Testcontainers (postgresql, minio) | 2.0.5 |
| JaCoCo | 0.8.15 |
| Spotless (google-java-format 1.36.1) | 3.10.3 |
| maven-pmd-plugin (PMD 7.17.0) | 3.28.0 |

**Imágenes Docker**

| Imagen | Uso |
|---|---|
| `maven:3.9.16-eclipse-temurin-25` | Compilación y desarrollo local |
| `eclipse-temurin:25.0.4_7-jre-alpine` | Runtime del backend |
| `postgis/postgis:17-3.6-alpine` | PostgreSQL 17 + PostGIS 3.6 (también en Testcontainers) |
| `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` | Almacenamiento de objetos ([ADR 0008](docs/adr/0008-imagenes-base-con-version-fija.md)) |

**GitHub Actions**

| Action | Versión |
|---|---|
| `actions/checkout` | v7.0.1 |
| `actions/setup-java` | v6.0.1 |
| `actions/upload-artifact` | v7.0.1 |
| `docker/login-action` | v4.6.0 |
| `docker/setup-buildx-action` | v4.4.1 |
| `docker/build-push-action` | v7.4.0 |

### Consulta de propuestas pendientes (P8)

`GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/propuestas?page=0&size=20`, solo Catedrático y Administrador (lectores confirmados por el grupo). Devuelve `puente_id`, `content`, `page`, `size`, `total_elements`, `total_pages`; únicamente PROPUESTA del puente solicitado, por ID descendente. Tamaño máximo 100; puente inexistente devuelve 404. Usa el mismo Bearer Token de la aceptación. No expone el historial completo ni genera propuestas. Ver [ADR 0026](docs/adr/0026-consulta-autorizada-de-propuestas.md) y el manual técnico enlazado arriba.

### Historial y detalle de órdenes (P8)

Solo Catedrático y Administrador, con sesión de cuenta activa, según acuerdo de lectores comunicado por el usuario:

- `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento?page=0&size=20`: todos los estados del puente, incluidos ejecutada/descartada/cancelada; página con `puente_id`, `content`, `page`, `size`, `total_elements`, `total_pages`. Tamaño máximo 100, orden por ID descendente.
- `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}`: detalle con prioridades, origen, fechas, responsable y motivo. Una orden de otro puente devuelve 404.

En Postman usa el Bearer Token de P2; sin cuerpo. Ambas rutas son de lectura y no habilitan acciones sobre las órdenes. La ruta `/propuestas` conserva su filtro y contrato. Ver [ADR 0028](docs/adr/0028-historial-y-detalle-autorizado-de-ordenes.md).

### Iniciar ejecución de una orden (P8)

`POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/inicio-ejecucion`, sin cuerpo y con Bearer Token de cuenta activa Catedrático/Admin (actores de inicio confirmados por el grupo). Solo PROGRAMADA → EN_EJECUCION; conserva programación/intervención/prioridades y devuelve el DTO persistido. La auditoría registra actor, fecha y cambio en la misma transacción.

Repetición o estado incompatible: 409; una orden de otro puente: 404; origen incompatible: 422; UUID inválido: 400; sin sesión: 401; otro rol: 403. No registra una fecha de cierre ni envía notificaciones nuevas. Ver [ADR 0029](docs/adr/0029-inicio-auditado-de-ejecucion-de-ordenes.md). Verificación tras el merge de P9: 755 pruebas backend y controles aprobados, incluidas 22 nuevas de inicio y 68 de Notificaciones.

### Evidencia y cierre de órdenes (P8)

Para Catedrático/Admin, `POST .../ordenes-mantenimiento/{ordenId}/evidencias` guarda foto y miniatura mediante P1 en PROGRAMADA o EN_EJECUCION. `GET .../{ordenId}/evidencias?page=0&size=20` consulta fotos no purgadas de esa orden, también después del cierre (máximo 100). `POST .../{ordenId}/cierre` recibe `fecha_ejecucion` ISO y `responsable`, exige EN_EJECUCION y al menos una foto guardada de esa orden; responde DTO EJECUTADA. El servidor cuenta las fotos y audita el cambio con bloqueo compartido con subida. No basta seleccionar una imagen ni enviar un contador.

Actores y ventana de carga confirmados por el equipo el 20/10; no asignan roles de otras operaciones. Contrato, errores, límites de almacenamiento y pruebas: [ADR 0030](docs/adr/0030-evidencia-y-cierre-autorizado-de-ordenes.md). Pasos de Postman en el [manual técnico P8](docs/manual-tecnico-p8.md). El frontend correspondiente aún requiere sesión/ruta protegida P2 y puente real P3 para abrirse desde la aplicación.

### Descartar una propuesta de mantenimiento (P8)

`POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/descarte`, JSON `{"motivo":"Intervención duplicada"}`, para Catedrático/Admin confirmados. Solo PROPUESTA pasa a DESCARTADA; se conservan fila, motivo y demás datos en el historial. Motivo vacío: 400; repetición/otro estado: 409; otra orden/puente: 404. Comparte bloqueo con aceptación y audita el cambio en la misma transacción. No habilita cancelación. Ver [ADR 0031](docs/adr/0031-descarte-auditado-de-propuestas.md) y [manual técnico](docs/manual-tecnico-p8.md) para prueba y verificación del merge de P3.

### Ajustar prioridad de una orden (P8)

`PATCH /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/prioridad`, JSON `{"prioridad":"ALTA","justificacion":"Se requiere atención urgente"}`. Según decisión explícita del usuario, Catedrático/Admin pueden ajustar en PROPUESTA, PROGRAMADA o EN_EJECUCION; terminales responden 409. Cada cambio real se justifica y audita; repetir el valor vigente no sustituye la razón previa ni duplica auditoría. Se conserva la sugerencia, incluso ausente; no se calcula su fórmula. [ADR 0032](docs/adr/0032-ajuste-autorizado-de-prioridad-de-ordenes.md), [manual técnico](docs/manual-tecnico-p8.md) y verificación: 939 backend y controles aprobados.

### Registrar o cambiar fecha programada (P8)

`PATCH /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/fecha-programada` recibe `{"fecha_programada":"2026-11-02"}`. Catedrático/Admin, exclusivamente PROGRAMADA: guarda fecha civil obligatoria, conserva el estado y audita cada cambio. Repetir la fecha no modifica actualizado_en ni duplica auditoría. Sin restricciones temporales adicionales, conforme a decisión explícita del usuario; la política no aparece detallada en el enunciado.

Estado incompatible: 409; padre ajeno/ausente: 404; origen inconsistente: 422; fecha/UUID inválidos: 400; sesión o rol no autorizado: 401/403. Se reutiliza P2, incluida baja/rol vigente de cuenta. Aceptar, programar fecha e iniciar son operaciones distintas; registrar una fecha no garantiza la periodicidad anual. Ver [ADR 0033](docs/adr/0033-programacion-auditada-de-fecha-de-ordenes.md) y [manual técnico con prueba Postman](docs/manual-tecnico-p8.md).

### Alerta de inspección vencida (P8)

RN-MTO-08: revisión diaria a las **06:00 America/Guatemala**, con @Scheduled/ShedLock. Al superar doce meses de calendario desde la última publicación, avisa a Catedráticos y Administradores activos mediante P9. Si nunca hubo publicación, usa el registro del puente. Son políticas adoptadas por el usuario para las omisiones del enunciado; el límite exacto no avisa.

Envía una notificación `INSPECCION_VENCIDA` por puente/destinatario/período, con código/nombre y enlace a `/puentes/{id}`. Una nueva publicación inicia otro plazo. La migración aditiva guarda marcas de envío con unicidad, incluso sin inspección previa; aviso y marca son atómicos. No duplica avisos al leerlos o ejecutar nuevamente el job. Los fallos se reintentan en la revisión diaria siguiente sin detener los demás puentes.

Se consulta mediante el GET autenticado existente `/api/v1/notificaciones?page=0&size=20` de P9 (lista `contenido`). No se añade endpoint para forzar el job ni pantalla de notificaciones propia de P8. El frontend P9 y la publicación P5 conservan sus tareas de integración; la programación rutinaria anual sigue pendiente. [ADR 0034](docs/adr/0034-alertas-de-inspeccion-vencida.md) y [manual técnico](docs/manual-tecnico-p8.md).

Verificación del incremento de alerta (21/10/2026): **1089 pruebas backend aprobadas**, incluidas 17 nuevas. Pasan Spotless, PMD, empaquetado y las tres reglas JaCoCo (servicios 95.16 %; núcleo IC/máquina 100 % según clases configuradas). El servicio de alerta tiene 100 % de líneas/ramas. Frontend sin cambios; no se acredita CI remoto.

### Recálculo fechado del Índice de Condición (P8)

- `GET /api/v1/inspecciones/{inspeccionId}/recalculos-ic?page=0&size=20`: devuelve el resultado original publicado inmutable (`original`) junto con la página de recálculos (`recalculos`) ordenada por ID descendente.
- `POST /api/v1/inspecciones/{inspeccionId}/recalculos-ic`: cuerpo `{"version_pesos_id":"..."}`, para Catedrático o Administrador. Calcula y registra un recálculo fechado bajo otro juego de pesos de la misma versión de formulario, conservando la anulación crítica e idempotencia por pareja `(inspeccion_id, version_pesos_id)` sin alterar la inspección original ni los campos derivados del puente (RN-IC-08). Detalle en [ADR 0043](docs/adr/0043-recalculo-fechado-del-indice-de-condicion.md).
