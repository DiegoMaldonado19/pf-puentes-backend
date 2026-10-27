# 0049. Veredictos y observaciones ancladas transaccionales

- Fecha: 27/10/2026.
- Estado: aceptado para este incremento.
- Reglas: RN-REV-03/04/05/06 y contrato de publicación de Inspecciones.

## Decisión

El revisor asignado, con cuenta activa y responsabilidad sobre el curso, consulta y modifica su ciclo. Las mutaciones adquieren el mismo bloqueo de inspección que la apertura, antes de cargar la entidad Revision; así no se decide sobre un estado cargado antes de esperar el bloqueo. Ciclos cerrados devuelven 409.

La aprobación usa `PublicacionInspeccionService.publicarPorAprobacion`. Cambios y rechazo usan `emitirVeredicto`. Observación, fecha, auditoría y publicación pertenecen a una sola transacción; un error de cálculo no deja un veredicto confirmado. La calificación no se expone ni se modifica en este incremento.

Las observaciones se validan contra los campos reales del esquema original. No se aceptan referencias elegidas libremente por el cliente, definiciones no utilizadas ni referencias ambiguas. Los arreglos son un campo completo: el anclaje por fila necesita acordar una identidad estable antes de implementarse. El texto se almacena normalizado y se renderiza escapado por Angular; no es HTML.

## Consecuencias y pruebas

Se conservan los ciclos previos y las observaciones al solicitar cambios; no se eliminan datos ni se editan migraciones fusionadas. El endpoint de detalle sirve los datos/formulario originales para el motor en lectura y la lista de campos para el marcador compartido. Archivos y resolución por el estudiante siguen pendientes.

Las pruebas usan PostgreSQL real y el servicio real de publicación/transiciones. Solo el puerto de cálculo se controla en los casos de aprobación para aislar calibración externa: se verifica persistencia del IC y rollback ante fallo, no se acredita calibración productiva.
