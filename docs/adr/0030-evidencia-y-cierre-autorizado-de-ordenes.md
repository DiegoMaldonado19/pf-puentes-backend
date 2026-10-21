# 0030. Evidencia y cierre autorizado de órdenes

- Estado: implementado; revisión e integración de acceso frontend pendientes
- Fecha: 20/10/2026
- Trazabilidad: HU-P8-MTO-05; RN-MTO-04/05; DT-BE-07/08/13, DT-SEC-08/09, DT-BD-12, DT-ALM-03/04, DT-CAL-01/02

## Acuerdos y decisión

El usuario confirmó que el grupo autoriza a **Catedrático y Administrador** para subir/consultar evidencia y cerrar órdenes. También confirmó carga en **PROGRAMADA y EN_EJECUCION** y consulta después del cierre. RN-MTO-05 exige evidencia para cerrar, pero no define esa ventana ni esos actores: se registran como acuerdos del equipo, sin atribuirlos al texto original. No se amplían permisos de descarte, cancelación, prioridad o programación.

`EvidenciaYCierreController` añade a `/api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}`:

- `POST /evidencias`, multipart con `SubirFotoDTO` de P1; 201 con `ArchivoDTO` y URL firmadas.
- `GET /evidencias?page=0&size=20`, página estable con `puente_id`, `orden_id`, `content`, `page`, `size`, `total_elements`, `total_pages`; tamaño máximo 100 y orden creado_en/id ascendente.
- `POST /cierre`, JSON `fecha_ejecucion` (fecha civil ISO) y `responsable`; 200 con DTO MapStruct persistido EJECUTADA.

La autorización se comprueba en controlador y servicios. Orden ajena/ausente: 404. Se valida el origen inspección/puente antes de mutar. Subir en otro estado o cerrar fuera de EN_EJECUCION: 409. UUID/metadatos/cierre incompletos o paginación inválida: 400; formatos de foto inválidos, falta de foto guardada o responsable >150 caracteres Unicode: 422; límites HTTP de tamaño existentes: 413. Sin sesión: 401; rol ajeno: 403. OpenAPI y Problem Details documentan el contrato.

## Archivos, transacción y auditoría

P8 reutiliza `ArchivoService.guardarEvidencia`, `AlmacenamientoArchivos` y captura de P1. No usa el endpoint de fotos de inspección. La foto y su miniatura se comprueban por firma JPEG/WebP; el cliente comprime con P1 y envía UUID v7, capturada_en y metadatos opcionales. Las URL son temporales; consultar de nuevo las renueva. No hay borrado de evidencia desde P8.

Se añade una **sobrecarga paginada** de `listarEvidencias`, conservando la firma de lista del contrato 16. Se corrige `contarEvidencias` para contar exclusivamente JPEG/WebP no purgadas de esa orden; la página aplica el mismo filtro en SQL antes de paginar. PDF, fotos ajenas o solo seleccionadas no satisfacen el cierre. No se traslada el cupo de 60 fotos por inspección a órdenes.

Subida y cierre usan el mismo bloqueo pesimista de la orden por puente + ID. El cierre espera a la subida en curso y cuenta solo fotos confirmadas. `OrdenMantenimientoService.cerrar` llama al bean separado `CierreOrdenService` después del bloqueo; usa `cambiarEstado` y el conteo del servidor. El cliente no fija el contador ni un estado arbitrario. Estado y auditoría CAMBIAR_ESTADO se confirman o revierten juntos. Dos cierres producen una transición y un 409, con una sola fila de auditoría.

`GuardadoEvidenciaService` reutiliza el aspecto de P2 para auditar CREAR sobre archivo. El indicador de creación procede de una consulta interna, nunca del cliente: reenviar el mismo UUID en la misma orden editable devuelve lo guardado sin duplicar objeto/bitácora. El UUID perteneciente a otro padre produce conflicto. Después del cierre, incluso un reenvío se rechaza por estado; se consulta la evidencia para reconciliar una respuesta incierta.

La atomicidad descrita es SQL. El almacenamiento de objetos de P1 no participa en esa transacción: un fallo entre objetos o después de subirlos puede dejar objetos sin metadatos confirmados. Es una limitación existente de P1, no una foto válida para cerrar; no se afirma rollback distribuido ni se habilita su eliminación desde el navegador.

## Límites y verificación

No se añaden migraciones, políticas de fecha futura/pasada, máximo de fotos por orden, notificaciones de cierre ni persistencia offline de mantenimiento. Se conservan programación, origen, intervención y prioridades. La UI usa permisos explícitos del host y falta integrarla con sesión/ruta de P2 y puente real de P3; la HU no se declara cerrada de extremo a extremo.

`EvidenciaYCierreTest`: 29 casos con PostgreSQL/PostGIS y MinIO reales, sin transacción implícita de prueba. Incluye subida/lectura/cierre y consulta posterior, actores permitidos/prohibidos, JPEG real descargado por URL firmada, ubicación y metadatos, reenvío sin duplicación, padre ajeno, estados, paginación/filtro SQL, PDF/purgadas/otra orden, firma inválida, campos, Unicode, rollback por bitácora, dos cierres y cierre concurrente con subida sin confirmar. La suite completa se registra en el [manual técnico](../manual-tecnico-p8.md#7-verificación-y-entrega). No se editan generados ni se versionan reportes de target.
