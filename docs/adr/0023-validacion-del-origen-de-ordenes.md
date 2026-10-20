# 0023. Validación del origen de órdenes

- Estado: propuesto para revisión; validación interna implementada, fachada pendiente
- Fecha: 19/10/2026
- Historia: HU-P8-MTO-01, existencia y correspondencia del origen
- Reglas/directrices: RN-MTO-01; DT-ARQ-03; DT-BE-05/06/10; DT-SEC-04

## Contexto

Las FK de `orden_mantenimiento` comprueban que puente e inspección existan, pero no que la inspección pertenezca al puente indicado. El repositorio ya está implementado ([ADR 0022](0022-persistencia-y-consulta-base-de-ordenes.md)); la fachada con permisos por rol/fila sigue pendiente del acuerdo del equipo.

## Decisión

`ValidacionOrigenOrden` es un componente interno de mantenimiento con transacción de solo lectura. `validar(puenteId, inspeccionId)` exige puente no nulo y existente; la inspección es opcional. Si se indica, debe existir y pertenecer al mismo puente.

Un puente o inspección inexistente produce `NegocioException` 404; origen de otro puente, 422. Los errores contienen tipo y detalle en el formato Problem Details común. El puente nulo se considera una entrada interna inválida (`IllegalArgumentException`); el futuro DTO HTTP deberá validarlo como campo requerido antes de llamar al dominio.

Las consultas nuevas del repositorio son SQL sobre IDs escalares, conforme a [ADR 0010](0010-modelo-de-datos-base.md), sin importar entidades JPA de inventario/inspecciones. No se cambia el esquema ni se interpreta JSON de P4. Tampoco se añade una exigencia general de inspección PUBLICADA aquí: MTO-02 deberá comprobar publicación al generar propuestas; esta pieza verifica el vínculo de origen de MTO-01.

## Uso y límites

La futura fachada debe **autorizar primero** al actor y su alcance por fila, invocar esta validación con datos obtenidos del servidor y persistir/auditar en la transacción de negocio. El componente no concede permisos ni expone un endpoint. No persiste órdenes y no se invoca automáticamente desde `Repository.save`: la integración con la fachada sigue pendiente y ningún flujo HTTP queda protegido por esta entrega por sí sola.

## Verificación

`ValidacionOrigenOrdenTest` usa PostgreSQL/PostGIS real y fixtures con rollback. Casos: origen válido, origen opcional, puente nulo, puente inexistente, inspección inexistente, inspección existente de otro puente y ausencia de escrituras durante la validación. Usa el fixture de persistencia ya disponible, no una publicación real de P5 ni un catálogo aprobado.

También se completa la verificación global que quedó interrumpida en la entrega anterior. Se selecciona el JDK 25 del repositorio y se limitan los procesos Java a 768 MB de heap/dos procesadores, con caché de contextos de prueba de ocho entradas, para reducir consumo de recursos. Son opciones de la ejecución local, no modificaciones al runtime de producción ni omisión de pruebas. No limitan la memoria total del equipo ni de los contenedores Docker.

Resultado del 19/10: 542 pruebas aprobadas sin fallos, errores ni omisiones, incluidas siete nuevas del validador y las 14 del repositorio anterior. Spotless, PMD, build y las tres reglas JaCoCo aprobados. El validador alcanzó 100 % de líneas y ramas. La suite completa terminó con los límites de recursos descritos; la causa del bloqueo previo del equipo no se determinó. MTO-01 continúa en progreso por autorización, fachada/transacción/auditoría, DTO/API y UI pendientes. No se modifica frontend para esta comprobación interna.

Comando de verificación usado en esta ejecución, con JDK 25 seleccionado y Docker disponible:

```sh
JAVA_TOOL_OPTIONS="-Xmx768m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=8" ./mvnw -B -ntp clean spotless:check verify pmd:check
```
