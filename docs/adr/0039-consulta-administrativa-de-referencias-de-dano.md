# 0039 — Consulta administrativa de referencias de daño

Fecha: 24/10/2026. Estado: implementado. Trazabilidad: RN-IC-02/03, RN-USR-10 y RN-FRM-05.

## Contexto

El adaptador del ADR 0038 obtiene referencias reales del esquema persistido, pero la pantalla de pesos solo permitía elegir versiones que ya tenían juegos. El Administrador necesita consultar también los elementos de versiones sin configuración. La completitud del juego y su vínculo con formularios publicados quedaron pendientes de consulta al grupo por decisión del usuario; no se habilita el guardado mientras se resuelven.

## Decisión

Se añade `GET /api/v1/formularios/{formularioId}/elementos-dano`, solo Administrador en controlador y servicio. Devuelve `PaginaPesosDTO<String>`: `content` es una lista de referencias canónicas, sin serializar entidades ni el esquema. Se reutiliza `MetadatosDanoFormulario` para validar y extraer; las referencias se ordenan de forma natural y se paginan desde cero, tamaño predeterminado 20 y máximo 100. La paginación también se valida en el servicio. Se carga el esquema completo para validar su unicidad antes de devolver cualquier página.

Sin daños declarados: página vacía. Página fuera del total: contenido vacío con total conservado. UUID/paginación inválidos: 400; sin sesión: 401; rol ajeno: 403; versión ausente: 404; esquema incompatible: 422. No crea juegos, audita lecturas, asigna pesos ni interpreta condiciones críticas. Lee borradores e históricos por ID y conserva las restricciones del extractor.

El frontend reutiliza el listado real de P4 para seleccionar cualquier versión. El catálogo existente de versiones con pesos conserva su significado y contrato. Esta operación prepara la futura creación; no implica que un formulario sin pesos esté listo para calcular IC.

## Verificación

Pruebas HTTP/servicio con PostgreSQL/PostGIS real: semilla de 19 referencias sin juegos previos, orden/paginación, vacío, ausencia, esquema incompatible, límites, roles y ausencia de escrituras/auditorías. Evidencia y resultados globales en el manual técnico P8. Sin migración ni cambios de workflow.
