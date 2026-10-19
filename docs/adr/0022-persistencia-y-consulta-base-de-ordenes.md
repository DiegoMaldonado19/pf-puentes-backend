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

El resultado ejecutado se registra en el avance de P8 al terminar la verificación.

## Consecuencias y pendientes

MTO-01 pasa a progreso por su base de persistencia; no queda completa. Falta servicio con permisos y consistencia de origen, DTO/MapStruct, API/OpenAPI y pantalla. Tampoco se cierran las HU de transiciones por guardar una entidad en pruebas. No se añade frontend para un repositorio interno ni una ruta de producción/demo. El siguiente incremento dependerá de la matriz de permisos acordada y de las entregas reales disponibles del equipo.
