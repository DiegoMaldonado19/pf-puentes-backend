# 0040 — Creación completa y versionada de pesos

Fecha: 25/10/2026. Estado: implementado; política adoptada con autorización explícita del responsable P8. Trazabilidad: RN-IC-03/08, RN-FRM-03/05, RN-USR-09/10 y DT-BE-05/06/07. Este ADR se versiona en el repositorio del backend para que el equipo lo reciba; no depende de documentos privados.

## Regla del enunciado y decisión adoptada

RN-IC-03 exige administración de pesos por Administrador, vínculo con la versión del formulario y calibración inicial en acta firmada por el Catedrático. RN-IC-08 exige conservar resultados publicados al cambiar pesos. El enunciado no detalla completitud del juego ni incorporación de juegos a formularios ya publicados.

El responsable P8 primero prefirió consultar esos detalles y después autorizó expresamente la recomendación. Se adopta lo siguiente; no se presenta como respuesta confirmada del grupo ni del catedrático:

1. Cada juego nuevo contiene exactamente un peso por cada campo de daño de su versión: sin faltantes, referencias ajenas ni duplicados.
2. Cada creación genera un juego nuevo. No hay edición o borrado de juegos históricos.
3. Se permite crear juegos para borradores, versiones activas y retiradas. No se modifica el esquema ni se publica o reactiva el formulario.
4. No se modifican pesos anteriores, referencias de pesos de inspecciones, IC ni estados publicados. No se disparan recálculos.
5. La selección del juego que usará una publicación y la compatibilidad tras editar un borrador siguen pendientes de coordinación con P5/P4. El juego recién creado no se convierte automáticamente en el oficial para inspecciones existentes.

## API y transacción

`POST /api/v1/formularios/{formularioId}/juegos-pesos`, exclusivo de Administrador en controlador y fachada `CreacionJuegoPesosService`. El cuerpo tiene `acta` opcional y `pesos: [{elemento_ref, peso}]`; no recibe un conjunto de referencias permitidas. DTO y MapStruct construyen los registros de dominio; los límites conservan las reglas existentes de Unicode y precisión sin redondeo implícito: peso positivo, máximo 999.999 y hasta tres decimales significativos.

La fachada inicia una transacción y obtiene bloqueo compartido de la fila `formulario_version` con `FOR SHARE`. Después extrae metadatos persistidos y comprueba igualdad exacta entre referencias recibidas y campos de daño. El bloqueo impide que una edición/eliminación concurrente del formulario se interponga entre la lectura y la escritura; permite creaciones concurrentes de juegos distintos. No congela permanentemente un borrador.

Reutiliza `ConfiguracionPesosService` y `CreacionPesoService`: insertan y auditan juego/pesos por fila mediante P2, en la misma transacción y correlación. La operación HTTP de creación debe entrar por esta fachada; el servicio previo con entradas normalizadas sigue siendo una pieza interna, no otro endpoint.

Respuesta 201 con `JuegoPesosDTO` y `Location: /api/v1/juegos-pesos/{id}`. 400 por cuerpo/UUID/peso/acta/referencias duplicadas inválidos; 401 sin sesión; 403 rol ajeno; 404 formulario ausente; 422 por esquema incompatible o referencias incompletas/ajenas. Un formulario sin daños no admite un juego vacío. Errores en RFC 7807, sin migraciones ni cambios de workflow.

## Acta y límites

`acta: null` conserva el significado existente de juego provisional. Una referencia no verifica existencia, firma o calibración; se informa en UI y documentación. La calibración oficial sigue requiriendo al Catedrático. No se generan pesos predeterminados ni se reutilizan los pesos de vulnerabilidad del manual como calibración.

El frontend consulta todas las páginas de referencias antes de construir el formulario. El backend vuelve a validar al guardar, por si cambió un borrador entre consultas. El cliente bloquea doble envío, salida y cambio de contexto durante el POST. No reintenta escrituras con resultado incierto: solicita consultar los juegos guardados antes de intentar otra creación.

## Verificación

Pruebas HTTP/BD de metadatos reales, completitud, validación, roles en controlador/servicio, auditoría y conservación de pesos anteriores y una inspección publicada. Se reutilizan pruebas existentes de rollback de juego/pesos/auditoría. Frontend prueba formulario, paginación completa de metadatos, contexto, guardia de salida y confirmación del servidor. El recorrido en Chrome crea dos juegos de 19 campos con API/BD reales y comprueba que el primero y los resultados anteriores se conservan; resultados finales en el manual técnico.
