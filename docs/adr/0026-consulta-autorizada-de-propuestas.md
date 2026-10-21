# 0026. Consulta autorizada de propuestas pendientes

- Estado: implementado, pendiente de revisión y entrega
- Fecha: 20/10/2026
- Historias: HU-P8-MTO-01/03
- Trazabilidad: RN-MTO-01/02/04/09; DTO/MapStruct, paginación, autorización en servicio y OpenAPI del proyecto.

## Contexto y acuerdo

El enunciado autoriza Catedrático y Administrador para aceptar propuestas, pero no enumera lectores de esta bandeja. El usuario comunicó la confirmación del grupo: **Catedrático y Administrador** pueden consultar las propuestas pendientes. Este acuerdo se aplica a esa bandeja; no determina los lectores del historial ni los actores de otras transiciones.

## Decisión

Se habilita `GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/propuestas?page=0&size=20`. Controlador y `ConsultaPropuestasService` verifican ambos roles. La fachada de solo lectura reutiliza `ConsultaOrdenes`, que valida existencia del puente y filtra PROPUESTA por puente en la base de datos antes de paginar/contar. No agrega una relación de mantenimiento con cursos ni ofrece filtros arbitrarios de estado.

Devuelve `puente_id`, `content` (DTO de órdenes mapeados con MapStruct), `page`, `size`, `total_elements`, `total_pages`, por ID descendente. `puente_id` conserva el origen incluso en una página vacía. Bean Validation aplica límites HTTP (página no negativa, tamaño 1–100; predeterminados 0/20); el componente interno también protege contra desbordamiento del desplazamiento. UUID/paginación inválidos: 400; falta de sesión: 401; otros roles: 403; puente ausente: 404. Los errores usan Problem Details. No se escribe auditoría por leer ni se modifican órdenes.

## Integración y límites

El frontend puede consultar esta ruta y recargarla después del POST de aceptación existente. La cuenta activa y el token se verifican mediante la seguridad global de P2; no se implementa otra sesión. El historial completo continúa interno. Generación automática, fecha de programación, evidencia y las demás operaciones conservan sus pendientes propios. No hay migración ni datos de demostración en producción.

## Verificación

`ConsultaPropuestasApiTest` verifica los dos lectores, rechazo de otros roles también en servicio, 401, contrato snake_case, paginación y orden, respuesta vacía, límites/desbordamiento, UUID inválido, puente ausente y ausencia de escrituras de auditoría. Se mantienen las pruebas de filtro, pertenencia y conservación del historial en `ConsultaPropuestasTest`.

Verificación global local del 20/10: 624 pruebas aprobadas, sin fallos/errores/omisiones; `clean spotless:check verify pmd:check` con JDK 25, PostgreSQL/PostGIS y MinIO. Spotless, PMD, build y las tres reglas JaCoCo aprobados.
