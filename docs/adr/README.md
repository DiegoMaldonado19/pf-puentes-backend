# Registro de decisiones de arquitectura (ADR)

Índice de los ADR de este repositorio, en orden de número.

| Número | Decisión | Archivo |
|---|---|---|
| 0001 | Despliegue en una EC2 con un proyecto Compose por entorno | [0001-despliegue-en-ec2-con-compose-por-entorno.md](0001-despliegue-en-ec2-con-compose-por-entorno.md) |
| 0002 | Pipeline CI/CD por etapas en GitHub Actions | [0002-pipeline-ci-cd-por-etapas.md](0002-pipeline-ci-cd-por-etapas.md) |
| 0003 | Versión y promoción de imágenes por contenido | [0003-version-y-promocion-de-imagenes.md](0003-version-y-promocion-de-imagenes.md) |
| 0004 | Configuración por entorno con GitHub Environments | [0004-configuracion-por-entorno-con-github-environments.md](0004-configuracion-por-entorno-con-github-environments.md) |
| 0005 | Desarrollo local con hot reload | [0005-desarrollo-local-con-hot-reload.md](0005-desarrollo-local-con-hot-reload.md) |
| 0006 | Java 25 y Spring Boot 4.1 | [0006-java-25-y-spring-boot-4.md](0006-java-25-y-spring-boot-4.md) |
| 0007 | Usuarios de base de datos | [0007-usuarios-de-base-de-datos.md](0007-usuarios-de-base-de-datos.md) |
| 0008 | Imágenes base con versión fija | [0008-imagenes-base-con-version-fija.md](0008-imagenes-base-con-version-fija.md) |
| 0009 | La miniatura de 300 px la genera el cliente | [0009-miniatura-generada-en-el-cliente.md](0009-miniatura-generada-en-el-cliente.md) |
| 0010 | Modelo de datos base para todos los módulos | [0010-modelo-de-datos-base.md](0010-modelo-de-datos-base.md) |
| 0011 | Catálogo del INE con su código como llave primaria | [0011-catalogo-ine-con-clave-natural.md](0011-catalogo-ine-con-clave-natural.md) |
| 0012 | Lombok en las entidades JPA | [0012-lombok-en-las-entidades.md](0012-lombok-en-las-entidades.md) |
| 0013 | URL firmadas de MinIO servidas por nginx | [0013-url-firmadas-de-minio-servidas-por-nginx.md](0013-url-firmadas-de-minio-servidas-por-nginx.md) |
| 0014 | Tabla shedlock con el esquema de la librería | [0014-tabla-shedlock-con-el-esquema-de-la-libreria.md](0014-tabla-shedlock-con-el-esquema-de-la-libreria.md) |
| 0015 | Auditoría por aspecto en la capa de servicio | [0015-auditoria-por-aspecto-en-la-capa-de-servicio.md](0015-auditoria-por-aspecto-en-la-capa-de-servicio.md) |
| 0016 | Núcleo puro del Índice de Condición | [0016-nucleo-puro-del-indice-de-condicion.md](0016-nucleo-puro-del-indice-de-condicion.md) |
| 0017 | Token de renovación sin rotación | [0017-token-de-renovacion-sin-rotacion.md](0017-token-de-renovacion-sin-rotacion.md) |
| 0018 | Cursos, asignaciones y contratos de integración de P7 | [0018-cursos-asignaciones-y-contratos-p7.md](0018-cursos-asignaciones-y-contratos-p7.md) |
| 0019 | Creación transaccional y auditada de pesos | [0019-creacion-transaccional-y-auditada-de-pesos.md](0019-creacion-transaccional-y-auditada-de-pesos.md) |
| 0020 | Consulta paginada de juegos de pesos | [0020-consulta-paginada-de-juegos-de-pesos.md](0020-consulta-paginada-de-juegos-de-pesos.md) |
| 0021 | Núcleo de transiciones de órdenes | [0021-nucleo-de-transiciones-de-ordenes.md](0021-nucleo-de-transiciones-de-ordenes.md) |
| 0022 | Persistencia y consulta base de órdenes | [0022-persistencia-y-consulta-base-de-ordenes.md](0022-persistencia-y-consulta-base-de-ordenes.md) |
| 0023 | Validación del origen de órdenes | [0023-validacion-del-origen-de-ordenes.md](0023-validacion-del-origen-de-ordenes.md) |
| 0024 | Ajuste justificado de prioridad de órdenes | [0024-ajuste-justificado-de-prioridad-de-ordenes.md](0024-ajuste-justificado-de-prioridad-de-ordenes.md) |
| 0025 | Aceptación autorizada y auditada de propuestas | [0025-aceptacion-autorizada-y-auditada-de-propuestas.md](0025-aceptacion-autorizada-y-auditada-de-propuestas.md) |
| 0026 | Consulta autorizada de propuestas pendientes | [0026-consulta-autorizada-de-propuestas.md](0026-consulta-autorizada-de-propuestas.md) |
| 0027 | Validación de configuraciones de pesos | [0027-validacion-de-configuraciones-de-pesos.md](0027-validacion-de-configuraciones-de-pesos.md) |
| 0028 | Historial y detalle autorizado de órdenes | [0028-historial-y-detalle-autorizado-de-ordenes.md](0028-historial-y-detalle-autorizado-de-ordenes.md) |
| 0029 | Inicio autorizado y auditado de ejecución de órdenes | [0029-inicio-auditado-de-ejecucion-de-ordenes.md](0029-inicio-auditado-de-ejecucion-de-ordenes.md) |
| 0030 | Evidencia y cierre autorizado de órdenes | [0030-evidencia-y-cierre-autorizado-de-ordenes.md](0030-evidencia-y-cierre-autorizado-de-ordenes.md) |
| 0031 | Descarte autorizado y auditado de propuestas | [0031-descarte-auditado-de-propuestas.md](0031-descarte-auditado-de-propuestas.md) |
| 0032 | Ajuste autorizado de prioridad de órdenes | [0032-ajuste-autorizado-de-prioridad-de-ordenes.md](0032-ajuste-autorizado-de-prioridad-de-ordenes.md) |
| 0033 | Programación auditada de fecha de órdenes | [0033-programacion-auditada-de-fecha-de-ordenes.md](0033-programacion-auditada-de-fecha-de-ordenes.md) |
| 0034 | Alertas de inspección vencida sin avisos duplicados | [0034-alertas-de-inspeccion-vencida.md](0034-alertas-de-inspeccion-vencida.md) |
| 0035 | Consulta del seguimiento rutinario anual | [0035-consulta-del-seguimiento-rutinario-anual.md](0035-consulta-del-seguimiento-rutinario-anual.md) |
| 0036 | Recordatorios de programación rutinaria anual | [0036-recordatorios-de-programacion-rutinaria-anual.md](0036-recordatorios-de-programacion-rutinaria-anual.md) |
| 0037 | Catálogo administrativo de versiones con pesos | [0037-catalogo-administrativo-de-formularios-con-pesos.md](0037-catalogo-administrativo-de-formularios-con-pesos.md) |
| 0038 | Metadatos de daño desde el formulario persistido | [0038-metadatos-de-dano-desde-el-formulario-persistido.md](0038-metadatos-de-dano-desde-el-formulario-persistido.md) |
| 0039 | Consulta administrativa de referencias de daño | [0039-consulta-administrativa-de-referencias-de-dano.md](0039-consulta-administrativa-de-referencias-de-dano.md) |
| 0040 | Creación completa y versionada de pesos | [0040-creacion-completa-y-versionada-de-pesos.md](0040-creacion-completa-y-versionada-de-pesos.md) |
| 0041 | Núcleo común de idempotencia | [0041-nucleo-comun-de-idempotencia.md](0041-nucleo-comun-de-idempotencia.md) |
| 0042 | Cancelación autorizada y auditada de órdenes programadas | [0042-cancelacion-auditada-de-ordenes-programadas.md](0042-cancelacion-auditada-de-ordenes-programadas.md) |
| 0043 | Recálculo fechado y comparable del Índice de Condición | [0043-recalculo-fechado-del-indice-de-condicion.md](0043-recalculo-fechado-del-indice-de-condicion.md) |
| 0044 | Estimación preliminar del IC y confirmación de estado con justificación | [0044-estimacion-preliminar-del-ic-y-confirmacion-con-justificacion.md](0044-estimacion-preliminar-del-ic-y-confirmacion-con-justificacion.md) |
| 0045 | Generación de propuestas de mantenimiento por daño, detector de anulaciones críticas P4 y fachada oficial de IC para P5 | [0045-generacion-de-propuestas-por-dano-y-deteccion-de-anulaciones.md](0045-generacion-de-propuestas-por-dano-y-deteccion-de-anulaciones.md) |
| 0046 | Idempotencia HTTP del PUT de borrador de inspección | [0046-idempotencia-del-put-de-borrador.md](0046-idempotencia-del-put-de-borrador.md) |
| 0047 | Idempotencia HTTP del POST de envío de inspección | [0047-idempotencia-del-post-de-envio.md](0047-idempotencia-del-post-de-envio.md) |
| 0048 | Replay exacto del alta y la corrección de inspecciones | [0048-idempotencia-del-alta-de-inspeccion.md](0048-idempotencia-del-alta-de-inspeccion.md) |
| 0049 | Replay de permanentes y acompañantes sin repetir efectos | [0049-idempotencia-de-permanentes-y-acompanantes.md](0049-idempotencia-de-permanentes-y-acompanantes.md) |
| 0050 | Idempotencia y reconciliación durable del PDF | [0050-idempotencia-y-reconciliacion-de-documentos.md](0050-idempotencia-y-reconciliacion-de-documentos.md) |
| 0051 | Idempotencia y reconciliación de foto y miniatura | [0051-idempotencia-y-reconciliacion-de-fotos.md](0051-idempotencia-y-reconciliacion-de-fotos.md) |
| 0052 | M09: replay autorizado y reconciliación de baja de archivo | [0052-idempotencia-de-baja-de-archivo.md](0052-idempotencia-de-baja-de-archivo.md) |
| 0053 | Replay autorizado de la baja lógica de borrador | [0053-idempotencia-baja-borrador.md](0053-idempotencia-baja-borrador.md) |
| 0054 | Preparación de calificaciones desde respuestas reales | [0054-preparacion-de-calificaciones-desde-respuestas-reales.md](0054-preparacion-de-calificaciones-desde-respuestas-reales.md) |
| 0055 | Bandeja y apertura transaccional de revisión académica | [0055-bandeja-y-apertura-de-revision-academica.md](0055-bandeja-y-apertura-de-revision-academica.md) |
| 0056 | Veredictos y observaciones ancladas transaccionales | [0056-veredictos-y-observaciones-ancladas.md](0056-veredictos-y-observaciones-ancladas.md) |
| 0057 | Seguimiento, calificación privada y avisos de revisión | [0057-seguimiento-calificacion-privada-y-avisos-de-revision.md](0057-seguimiento-calificacion-privada-y-avisos-de-revision.md) |
| 0058 | Validación del formulario desde su esquema, no con rutas fijas | [0058-validador-de-formulario-desde-el-esquema.md](0058-validador-de-formulario-desde-el-esquema.md) |
| 0059 | Cuenta de servicio de MinIO, contenedor como uid 1000 y bitácoras JSON rotadas | [0059-cuenta-de-servicio-minio-y-logs-json.md](0059-cuenta-de-servicio-minio-y-logs-json.md) |
| 0060 | Módulos agregados fuera de DT-ARQ-03: curso, notificacion e indice | [0060-modulos-agregados-curso-notificacion-indice.md](0060-modulos-agregados-curso-notificacion-indice.md) |
| 0061 | Coordenadas de puentes en Malo públicas, sin restricción por estado | [0061-coordenadas-de-puentes-en-malo-publicas.md](0061-coordenadas-de-puentes-en-malo-publicas.md) |
| 0062 | Identificación del puente desde el inventario, no desde la captura | [0062-identificacion-desde-el-inventario.md](0062-identificacion-desde-el-inventario.md) |
| 0063 | Contrato 15 implementado; desvío SQL de titularidad cerrado | [0063-archivos-lee-inspeccion-por-sql-hasta-la-entrega.md](0063-archivos-lee-inspeccion-por-sql-hasta-la-entrega.md) |
| 0064 | El mapeo entre versiones se aplica en la precarga | [0064-mapeo-entre-versiones-en-la-precarga.md](0064-mapeo-entre-versiones-en-la-precarga.md) |
| 0065 | Edición, borrado y moderación también en hilos de inspección; menciones por id | [0065-edicion-y-moderacion-en-hilos-de-inspeccion.md](0065-edicion-y-moderacion-en-hilos-de-inspeccion.md) |
| 0066 | Bitácora append-only también en Hibernate | [0066-bitacora-append-only-en-hibernate.md](0066-bitacora-append-only-en-hibernate.md) |
