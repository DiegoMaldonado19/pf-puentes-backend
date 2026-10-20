# 0022. Persistencia y consulta base de órdenes

- Estado: propuesto para revisión; repositorio implementado, servicio/API pendientes
- Fecha: 19/10/2026
- Historia: HU-P8-MTO-01, persistencia base; soporte a MTO-03/04/05/06
- Reglas/directrices: RN-MTO-01/03/09; DT-ARQ-03; DT-BD-05/08/09/10/11; DT-BE-08/09; DT-CAL-02

## Contexto

Las órdenes ya tienen entidad, migración y núcleo de transiciones ([ADR 0021](0021-nucleo-de-transiciones-de-ordenes.md)). Falta persistirlas y recuperar el historial por puente. La matriz de lectura/ejecución y el alcance por fila siguen pendientes; no corresponde crear un CRUD HTTP sin esos acuerdos.

## Decisión

`OrdenMantenimientoRepository` es interno al módulo y extiende el marcador `Repository`, declarando únicamente `save`, `findById` y `findByPuenteId(UUID, Pageable)`. No hereda `delete`, `deleteAll` ni un `findAll` sin página. Es una restricción del contrato Java, no una prohibición absoluta de SQL directo por permisos de base de datos.

La consulta por puente no filtra estados terminales: las órdenes DESCARTADA y CANCELADA siguen consultables con sus motivos. El orden y tamaño de página los suministra el llamador; el futuro servicio/API validará su máximo de 100. Un ID ausente devuelve `Optional.empty`; una colección sin resultados devuelve página vacía. La traducción a 404 para un puente inexistente corresponde al servicio futuro.

La entidad conserva IDs escalares de puente e inspección, siguiendo el aislamiento entre módulos; no se importan entidades JPA de P3/P5. No hay asociaciones que requieran cargas perezosas ni un `EntityGraph` artificial para esta consulta. Se mantienen tablas, índices y restricciones existentes sin editar migraciones ni diccionario.

El repositorio persiste cambios ya validados por `OrdenMantenimiento.cambiarEstado`, sin reintroducir setter de estado. No audita ni autoriza por sí mismo. La futura fachada de servicio debe resolver permisos, auditoría, transacción, correspondencia entre inspección y puente y evidencia real. Las FK actuales garantizan existencia, pero no que una inspección pertenezca al mismo puente: eso sigue pendiente del servicio, como indican los criterios de MTO-01.

## Verificación

`OrdenMantenimientoRepositoryTest` usa PostgreSQL/PostGIS real mediante `PruebaIntegracion`, `@DataJpaTest` y fixtures con rollback. Comprueba UUID v7, marcas de tiempo y todos los datos consultables; origen opcional; cuatro tipos; paginación y separación por puente; conservación de motivos y estados terminales; IDs ausentes; persistencia de una transición validada; ausencia de borrado en el contrato; restricciones de puente obligatorio, FK de puente/inspección y cambio de prioridad sin justificación.

La orden CANCELADA del fixture es un antecedente sintético cargado solo en pruebas; su lectura no habilita una transición de cancelación cuyo origen aún no está acordado. La inspección publicada y el formulario también son fixtures, no publicación de P5 ni un esquema/calibración oficial. No se verifica aceptación/publicación real ni cierre con evidencia en esta entrega.

Con JDK 25 y Docker disponibles:

```sh
./mvnw -B -ntp -Dtest=OrdenMantenimientoRepositoryTest test
./mvnw -B -ntp clean spotless:check verify pmd:check
```

La primera ejecución global se interrumpió, aunque sus 14 casos nuevos pasaron. La comprobación se completó el 19/10 junto con el validador de origen ([ADR 0023](0023-validacion-del-origen-de-ordenes.md)): 542 casos sin fallos, errores ni omisiones; Spotless, PMD, build y tres reglas JaCoCo aprobados. No se acredita una ejecución previa de 535 casos como completada.

## DTO de lectura incorporado el 19/10

`OrdenMantenimientoDTO` y `OrdenMantenimientoMapper` preparan la salida de lista/detalle exigida por MTO-01. MapStruct realiza la conversión de entidad a DTO; no hay conversión inversa para modificar órdenes ni estado desde este modelo.

El DTO serializa exactamente estos campos: `id`, `puente_id`, `inspeccion_id`, `tipo`, `estado`, `descripcion`, `elemento_ref`, `prioridad_sugerida`, `prioridad`, `justificacion_prioridad`, `fecha_programada`, `fecha_ejecucion`, `responsable`, `motivo`, `creado_en` y `actualizado_en`. Son los datos existentes del modelo, sin añadir identificadores o fechas inventados. Origen y demás valores ausentes se conservan como `null`; las marcas temporales se entregan en UTC y las fechas de calendario en ISO. La presentación de Guatemala corresponde al frontend.

Las pruebas de contrato usan el mapper generado y Jackson configurado por Spring Boot: verifican nombres y conjunto exacto de campos, valores de cierre/prioridades/origen, timestamps UTC sin conversión de presentación, ausencias sin valores ficticios y motivo de descarte. Los metadatos de identidad/timestamps de prueba se preparan sin introducir setters en el modelo productivo. Estas pruebas no acreditan cierre con evidencia física ni autorización.

Verificación de esta ampliación: tres pruebas nuevas de contrato aprobadas y suite global de 545 casos sin fallos, errores ni omisiones; formato, PMD, build y las tres reglas JaCoCo aprobados. Se usó compilación limpia con JDK 25, heap máximo de 768 MB/dos procesadores por JVM y caché de ocho contextos. Un archivo generado inválido de otro módulo en `target` se regeneró al limpiar; no se modificó su fuente.

Esto aún no publica un endpoint ni un contrato HTTP activo: falta integrarlo en el servicio autorizado y en el controlador. No se creó un cliente frontend para URLs inexistentes ni una pantalla sin permisos.

## Consulta interna incorporada el 19/10

`ConsultaOrdenes` conecta el repositorio, el validador de existencia y MapStruct en una transacción de solo lectura. Es un componente de paquete, no la fachada de autorización ni un controlador. Su futuro llamador debe comprobar primero el actor y su acceso al puente.

- `listar(puenteId, pagina, tamano)` devuelve `Page<OrdenMantenimientoDTO>`, ordenada por ID descendente. Página desde cero, tamaño 1–100 y desplazamiento representable por JPA; entradas de paginación inválidas producen 400. El puente debe existir (404 en caso contrario). Una colección vacía o una página posterior al final se devuelven con sus totales.
- `obtener(puenteId, ordenId)` usa una consulta conjunta por ambos IDs. Una orden inexistente o de otro puente devuelve 404; no se devuelve primero una orden ajena para filtrar en memoria. Los IDs internos nulos se rechazan antes de consultar.

No se filtran órdenes descartadas/canceladas ni se escriben órdenes o auditorías de mutación al leer. No hay selección de roles, filtros inventados, DTO de creación ni API nueva. Las pruebas PostgreSQL recorren consultas, MapStruct y DTO, incluyendo páginas, aislamiento, recursos ausentes, límites, metadatos históricos y ausencia de escrituras. Esto comprueba pertenencia al puente, **no** autorización del usuario: seguirá pendiente integrar la fachada con la matriz de permisos acordada.

Verificación de la consulta interna: 14 casos nuevos aprobados y suite global de 559 sin fallos, errores ni omisiones. Spotless, PMD, build y tres reglas JaCoCo aprobados con JDK 25 y los límites locales de memoria/CPU/contextos ya documentados. No se repitieron pruebas frontend porque no hubo cambios en ese repositorio.

## Consecuencias y pendientes

MTO-01 pasa a progreso por su base de persistencia; no queda completa. Falta integrar las consultas internas, la validación de origen y el DTO/MapStruct ya construidos en la fachada con permisos, API/OpenAPI y pantalla. Tampoco se cierran las HU de transiciones por guardar una entidad en pruebas. No se añade frontend para un repositorio interno ni una ruta de producción/demo. El siguiente incremento dependerá de la matriz de permisos acordada y de las entregas reales disponibles del equipo.

## Integración posterior — 19/10/2026

La aceptación PROPUESTA → PROGRAMADA ya dispone de fachada autorizada, auditoría y POST con los roles explícitos de RN-MTO-02; ver [ADR 0025](0025-aceptacion-autorizada-y-auditada-de-propuestas.md). Las referencias anteriores a ausencia de servicios/API describen el punto de partida de este ADR. La consulta general y las demás operaciones mantienen sus pendientes; no se introduce un filtro de mantenimiento por curso no prescrito por el enunciado.

## Filtro interno de propuestas — 19/10/2026

`ConsultaOrdenes.listarPropuestas` utiliza `findByPuenteIdAndEstado` con estado fijo PROPUESTA en SQL/JPA. Comparte validación de puente y página (máximo 100), orden ID descendente y MapStruct con la consulta interna existente. La consulta cuenta y pagina pendientes, sin traer todo el historial para filtrarlo en memoria. Una aceptada deja de contarse; la fila y su historial siguen consultables por los métodos anteriores. No escribe ni audita mutaciones.

El usuario confirmó que el grupo aún no responde sobre lectores. No se habilita un GET ni se atribuye un permiso de lectura a los roles de aceptación por suposición. Diez casos PostgreSQL verifican los seis estados, puente, paginación/totales, desaparición del pendiente tras cambio de estado, vacíos, validación y ausencia de escrituras. Verificación global con JDK 25: `./mvnw -B -ntp clean spotless:check verify pmd:check`, 612 casos sin fallos/errores/omisiones; formato, PMD, build y las tres reglas JaCoCo cumplidos.
