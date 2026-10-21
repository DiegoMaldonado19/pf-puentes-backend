# 0028. Historial y detalle autorizado de órdenes

- Estado: implementado; pendiente de revisión y entrega
- Fecha: 20/10/2026
- Historia: HU-P8-MTO-01; RN-MTO-01/03/04/09; DT-ARQ-04, DT-BE-03/04/08/11/12 y DT-SEC-03/04

## Contexto y acuerdo

La consulta interna y MapStruct ya estaban implementados, pero solo se exponía la bandeja de propuestas. El usuario confirmó que el grupo autoriza **Catedrático y Administrador para consultar también el historial completo y el detalle**, incluidos estados terminales. Es una confirmación distinta de la lectura de propuestas. No establece actores de inicio/cierre/evidencia, descarte/cancelación o ajustes.

## Decisión

Se habilitan dos rutas, sin operaciones de escritura:

- `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento?page=0&size=20`: todos los estados del puente, por ID descendente; página vacía si el puente existe y no tiene órdenes. Valida existencia del puente; tamaño entre 1 y 100, página no negativa y desplazamiento representable. No añade filtros arbitrarios ni una relación orden → curso.
- `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}`: DTO completo de una orden, también ejecutada/descartada/cancelada. Busca por orden y puente en una sola consulta; una orden de otro puente o inexistente devuelve 404.

Controlador y `ConsultaOrdenesService` exigen los roles acordados. La fachada de solo lectura delega en `ConsultaOrdenes`; el mapeo entidad → DTO sigue a cargo de MapStruct. Las entidades JPA no se serializan. UUID/paginación inválidos: 400; sin sesión: 401; otros roles: 403; recurso ausente: 404, mediante Problem Details. Las consultas no modifican estado ni escriben auditoría de mutaciones.

La página devuelve `puente_id`, `content`, `page`, `size`, `total_elements`, `total_pages`. Se generalizan los DTO Java de página/paginación a `PaginaOrdenesDTO` y `PaginacionOrdenesDTO`, reutilizados por la ruta de propuestas, **sin modificar su JSON ni URL**. La prioridad sugerida ausente sigue siendo null. Origen, fechas y motivos se conservan; leer CANCELADA no habilita una transición hacia ella.

## Integración y límites

El frontend incorpora cliente y consulta de historial/detalle. El host de sesión de P2 aportará permiso/interceptores y la selección real de puente; no se agrega login alternativo ni ruta pública. El historial es de lectura y no concede acciones de aceptación/inicio/cierre por mostrar una orden.

La seguridad global de P2 comprueba token y cuenta activa por petición. No se modifican migraciones, reglas de dominio, P7 ni almacenamiento de P1. El paso no implementa la generación desde publicación ni cierra MTO-01 de extremo a extremo mientras falten acceso real y entrega/revisión.

## Verificación

`ConsultaOrdenesApiTest`: ambos lectores autorizados, otros roles rechazados también al llamar directamente al servicio, 401 en ambas rutas, paginación/orden/totales, todos los estados, colección vacía, UUID inválidos, límite/desbordamiento, puente ausente, orden de otro puente, fechas/responsable/motivo conservados y ausencia de escrituras. Las pruebas existentes de propuestas conservan su contrato tras compartir los DTO.

Verificación backend completa: **665 pruebas**, sin fallos/errores/omisiones, incluidas 14 nuevas de la API; JDK 25 con PostgreSQL/PostGIS y MinIO reales. Pasaron compilación limpia, formato, PMD, empaquetado y las tres reglas JaCoCo. Comando: `./mvnw -B -ntp clean spotless:apply verify pmd:check`; se comprobó después `spotless:check`. Los reportes permanecen en `target/` y no se versionan.
