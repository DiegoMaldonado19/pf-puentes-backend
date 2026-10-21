# 0033. Programación auditada de fecha de órdenes

- Estado: implementado; acceso frontend integrado y revisión/entrega pendientes
- Fecha: 21/10/2026
- Trazabilidad: HU-P8-MTO-03; RN-MTO-01/04/07; DT-BE-04/07/13, DT-BD-12, DT-CAL-01/02/03

## Necesidad y decisión

Una propuesta aceptada puede quedar PROGRAMADA sin fecha. Se necesita registrar o cambiar la fecha de intervención. RN-MTO-04 define el ciclo y RN-MTO-07 exige periodicidad rutinaria máxima anual, pero no detallan actores ni ventana de edición de esta fecha.

El usuario adoptó expresamente: **Catedrático/Admin, solo PROGRAMADA, fecha obligatoria y sin restricciones temporales adicionales**. No es una regla literal del enunciado ni una confirmación del grupo que no se recibió. Se permite fecha pasada o futura, sin exigir que sea posterior a la inspección o a la aceptación. No se permite borrar la fecha. La fecha programada no sustituye fecha de ejecución ni acredita por sí sola cumplimiento anual.

## Contrato y transacción

`PATCH /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/fecha-programada` recibe `{"fecha_programada":"2026-11-02"}` y devuelve el DTO MapStruct completo con 200. Usa LocalDate/DATE, sin hora o conversión UTC. No cambia estado, tipo, descripción, origen, referencias, prioridades, justificación, responsable, motivo ni archivos. Aceptación y programación siguen siendo operaciones separadas.

Controlador y fachada autorizan los roles. La fachada bloquea la fila por orden y puente y comprueba pertenencia de la inspección opcional antes de llamar a `ProgramacionOrdenService`. El bean separado usa transacción MANDATORY, valida PROGRAMADA y fecha no nula, y registra MODIFICAR mediante el aspecto compartido de P2. Instantáneas anterior/nueva incluyen la fecha, actor y marcas de auditoría; un fallo de bitácora revierte el cambio. No se escriben auditorías directamente ni se modifica la máquina.

Repetir la fecha vigente devuelve lo persistido sin alterar actualizado_en ni añadir auditoría (`condicion = #result`). El estado se valida antes del no-op: una orden que dejó PROGRAMADA responde 409 aunque conserve esa misma fecha. El bloqueo compartido serializa programación con inicio, evidencia y otras decisiones; dos solicitudes iguales generan un único cambio/bitácora. No se introduce un contrato de versión esperada: las fechas distintas se aplican en el orden en que adquieren el bloqueo.

400: UUID, fecha obligatoria o formato inválidos; 401: sin sesión/token inválido/cuenta inactiva; 403: rol vigente no autorizado; 404: orden ausente o de otro puente; 409: estado diferente a PROGRAMADA; 422: origen inconsistente. La entrada Java directa también rechaza fecha nula. OpenAPI y RFC 7807 conservan las convenciones existentes.

## Compatibilidad con P2 y límites

El pull incorpora administración de usuarios e invitaciones, baja y consulta de auditoría de P2. Se reutilizan JWT/filtro reales y la lectura de rol/cuenta activa desde base de datos: un rol privilegiado antiguo en el JWT no elude un cambio de rol ni una baja posterior. No se modifica seguridad compartida ni se inventa un login P8.

No hay migraciones ni cambios en workflow. No se genera una orden ni se acepta una propuesta desde este PATCH. Siguen pendientes el emisor real de publicación de P5, metadatos de P4, catálogo y prioridad sugerida acordados, garantía rutinaria anual, alertas y origen de cancelación. MTO-03 continúa parcial por acceso integrado, flujo real y entrega; MTO-08 no se da por iniciada o cerrada solo por poder editar una fecha.

## Verificación

`ProgramacionOrdenTest` usa PostgreSQL/PostGIS y transacciones reales: JWT de ambos roles, rechazo de otros roles, rol cambiado tras emisión, baja mediante la API P2 y rechazo posterior, estados, fechas civiles, nulidad/formato, padre/origen, conservación, repetición, rollback y concurrencia. El escenario aceptar → programar conserva dos decisiones auditadas. Resultados globales y guía Postman: [manual técnico P8](../manual-tecnico-p8.md).

Verificación global: **1072 pruebas backend**, sin fallos/errores/omisiones; Spotless, PMD, empaquetado y tres reglas JaCoCo aprobados. Servicios: 95.07 % de líneas; programación: 100 % de líneas/ramas; núcleo IC/máquina conservan 100 % según clases configuradas. Se verificó una copia temporal idéntica de fuentes/pruebas para evitar bytecode concurrente del IDE. No acredita CI remoto.
