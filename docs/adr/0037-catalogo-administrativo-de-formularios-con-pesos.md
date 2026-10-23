# 0037. Catálogo administrativo de versiones con pesos

- Fecha: 23/10/2026
- Estado: implementado; resultados finales en README/manual técnico
- Trazabilidad: HU-P8-IC-04, RN-IC-03, DT-BE-04/05/08/09/12, DT-FE-06/11

## Contexto

P2 ya entregó sesión e interceptores/guardias frontend. La vista de pesos P8 consulta juegos y elementos de una versión concreta, pero P4 aún no entrega un selector/API de versiones. Pedir al usuario un UUID no ofrece una navegación apropiada. La consulta necesita referencias existentes, sin inventar versiones ni asumir esquema o calibración.

## Decisión

Se incorpora `GET /api/v1/juegos-pesos/formularios?page=0&size=20`, exclusivo de Administrador en controlador y servicio. Devuelve el contrato paginado existente con referencias `id`/`codigo` de versiones que tengan al menos un juego guardado. Incluye versiones históricas y borradores con configuraciones; no selecciona una versión activa ni certifica publicación o calibración.

El repositorio de P8 consulta con SQL la tabla vecina, sin importar su entidad JPA ni exponer esquema/mapeo. `EXISTS` evita duplicados cuando una versión tiene varios juegos; consulta y conteo están paginados, ordenados por código e ID. Proyección y DTO se convierten mediante MapStruct. Sin migración, escrituras, auditoría de mutación, semillas o catálogo alternativo de P4.

El máximo sigue siendo 100 filas; se rechazan parámetros inválidos y desplazamientos superiores al admitido por JPA. La validación de desplazamiento se añade al DTO compartido de paginación de pesos, también para sus colecciones anteriores. No se corrigen silenciosamente entradas inválidas.

## Integración y límites

Frontend `/admin/pesos` elige una referencia por código y reutiliza las API existentes de juegos y elementos. Se protege con la guardia del padre administrativo de P2, token en memoria e interceptores reales. Solo se consulta; creación HTTP/edición, acta calibrada y metadatos confiables P4 conservan sus pendientes. Un catálogo vacío es una respuesta válida, no una razón para insertar pesos de prueba en producción.

## Verificación

Pruebas PostgreSQL de referencias únicas, orden/paginación, versión sin juegos excluida, ausencia de configuraciones, campos no expuestos, límites, desplazamiento extremo, roles/sin sesión, autorización directa del servicio y lectura sin auditoría de escritura. La suite completa y controles se registran al finalizar en el manual técnico. El frontend prueba el recorrido completo con respuestas HTTP controladas; no equivale a navegador con backend real.
