# 0050. Seguimiento, calificación privada y avisos de revisión

- Fecha: 27/10/2026.
- Estado: aceptado para este incremento.
- Reglas: RN-REV-05/06/07/08, DT-SEC-04 y DT-BE-14.

## Historial y pendientes

Cada reenvío conserva las revisiones anteriores y abre otra fila por ciclo. El historial se pagina por creación e identificador descendentes; no incluye calificaciones. Acceden el autor, el catedrático responsable y los revisores registrados con cuenta activa. Las notas de ciclos aún sin veredicto se ocultan al autor y a terceros autorizados que no son su revisor.

Los pendientes del autor son observaciones emitidas y aún no resueltas; no se divulgan notas de una revisión en curso. La resolución bloquea la inspección, exige autoría y BORRADOR y registra `resuelta_en` sin borrar texto ni ciclo. Repetir una resolución propia ya confirmada devuelve la misma fecha. No modifica las respuestas ni reenvía la inspección: corregir y enviar sigue siendo responsabilidad de Inspecciones. El listado propio de inspecciones con revisión mantiene accesible el historial aunque ya no queden pendientes.

## Calificación

Se usa la columna existente, opcional, 0–100 y hasta dos decimales. Un endpoint separado solo la devuelve al autor o al revisor concreto del ciclo; ser Administrador o responsable actual de otro ciclo no otorga acceso. El revisor asignado y responsable actual puede registrarla, ajustarla o retirarla con null, incluso tras emitir el veredicto, sin alterar la decisión ni sus fechas.

La mutación usa la auditoría común, pero `calificacion` se excluye del saneamiento central de snapshots: de lo contrario, los administradores de bitácora podrían leerla. Se prueba esta barrera además de proteger DTO y endpoints. No se escriben notas numéricas en logs ni notificaciones.

## Aviso de 15 días

Job diario a las 06:30 America/Guatemala, con ShedLock y reloj controlable. El corte resta 15 días naturales en esa zona, incluidos fines de semana; el límite exacto es inclusivo. Incluye ENVIADA/EN_REVISION sin veredicto final, excluye eliminadas, autores no estudiantes, cuentas responsables inactivas y recursos sin matrícula. No depende de que el curso siga vigente.

Se llama a `NotificacionService.notificar` con tipo REVISION_VENCIDA y enlace `/revisiones`. La nueva tabla técnica `aviso_revision` usa una clave compuesta por inspección, fecha del envío y destinatario. Su objetivo es idempotencia, no representar una nueva entidad de negocio con UUID: un reenvío o cambio de destinatario puede generar otro aviso; ejecutar otra vez el mismo envío no duplica. La marca y la notificación se confirman juntas; si falla notificar, se revierte para permitir el siguiente intento. No se reescriben migraciones existentes.

El bloqueo de inspección coordina la selección del aviso con transiciones y resoluciones. La entrega es persistencia en el servicio compartido de notificaciones, no un SMTP independiente.
