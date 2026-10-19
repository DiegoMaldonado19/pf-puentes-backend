# 0020. Consulta paginada de juegos de pesos

- Estado: propuesto para revisión; implementado
- Fecha: 19/10/2026
- Historia: HU-P8-IC-04, consulta administrativa
- Reglas/directrices: RN-IC-03; DT-ARQ-04; DT-BE-03/04/05/08/09/10/11/12; DT-SEC-04

## Contexto

El servicio interno de creación ya guarda juegos nuevos con referencias normalizadas ([ADR 0019](0019-creacion-transaccional-y-auditada-de-pesos.md)). No hay contrato real de P4 para extraer campos permitidos desde el servidor. Consultar configuraciones existentes puede implementarse sin inventar ese contrato ni exponer creación HTTP.

## Decisión

Se añaden tres rutas GET, protegidas para Administrador tanto en controlador como en servicio:

- `/api/v1/formularios/{formularioId}/juegos-pesos`: el UUID identifica una **versión** de formulario. Orden fijo por ID descendente, sin seleccionar automáticamente un juego vigente.
- `/api/v1/juegos-pesos/{id}`: metadatos de un juego.
- `/api/v1/juegos-pesos/{id}/pesos`: elementos del juego, por referencia ascendente.

Las colecciones aceptan `page` desde cero y `size` entre 1 y 100, con valores predeterminados 0 y 20. Un DTO de entrada con Bean Validation rechaza formatos o límites inválidos con 400. No se expone ordenación arbitraria. El servicio usa `PageRequest` y los repositorios reciben `Pageable`; la consulta de pesos conserva su `EntityGraph` explícito. Ambas ordenaciones son estables: ID único y referencia única dentro de cada juego.

MapStruct convierte entidades a DTO dentro de la transacción de solo lectura. Se devuelve un contrato de página explícito con `content`, `page`, `size`, `total_elements` y `total_pages`, sin serializar entidades ni detalles internos de `PageImpl`. Los campos compuestos del JSON usan snake_case. La referencia de acta y `vigente_desde` son datos guardados: no certifican una firma ni una política de selección vigente.

Un formulario existente sin configuraciones devuelve página vacía; formulario o juego inexistente devuelve 404 RFC 7807, incluso al consultar sus pesos. Las rutas están anotadas para OpenAPI. No se registran auditorías de escritura para lecturas ni se modifican configuraciones.

## Verificación

`ConsultaPesosTest` recorre MockMvc, la seguridad real, servicios, mapeadores y PostgreSQL/Testcontainers. Comprueba paginación y separación por formulario/juego, valores históricos, acta ausente, páginas vacías, límites y formatos, 400/404 RFC 7807, 401 y 403 para cada ruta, y denegación directa del servicio para los otros roles y ausencia de sesión. Usa el helper de autenticación de P2; no sustituye las pruebas de emisión/validación JWT ya existentes. Las consultas no generan auditoría de escritura. Los fixtures ficticios se revierten al terminar cada prueba.

Resultado del 19/10: 21 casos focalizados aprobados. `./mvnw -B -ntp spotless:check verify pmd:check` aprobado con 461 casos de suite, incluidos 181 de `indice/`, sin fallos, errores ni omisiones. `ConsultaPesosService` alcanzó 100 % de líneas y ramas; se cumplieron ambas reglas JaCoCo. Formato y PMD aprobados. La ejecución inicial sin acceso al socket Docker falló antes de ejecutar los casos; se repitió con acceso autorizado a Docker y pasó.

## Consecuencias

Hay API de lectura para una futura pantalla administrativa. Sigue pendiente creación HTTP con metadatos confiables de P4, acuerdos de versiones/completitud, calibración inicial y pantalla. La historia completa continúa en progreso. Esta entrega se limita al backend; la pantalla de consulta puede abordarse como la siguiente entrega pequeña.
