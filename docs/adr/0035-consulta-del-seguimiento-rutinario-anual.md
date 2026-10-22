# 0035. Consulta del seguimiento rutinario anual

- Estado: consulta y componentes implementados; host con acceso protegido e integración final pendientes. Recordatorios añadidos en ADR 0036
- Fecha: 21/10/2026
- Trazabilidad: HU-P8-MTO-08, RN-MTO-07, DT-BE-04/08/09/10, DT-BD-07

## Decisiones y alcance

El usuario reportó que el grupo coincidió con la propuesta de contar el año desde la última ejecución de un mantenimiento RUTINARIO o, sin ejecuciones previas, desde el registro del puente. Se incorpora esa confirmación para la fecha base. La consulta para Catedrático/Admin y su criterio de programación habían sido delegados al criterio del asistente: no se atribuyen a una confirmación adicional del grupo ni al texto literal del enunciado.

Se toma la máxima fecha_ejecucion de RUTINARIO/EJECUTADA de ese puente. Otras tipologías, propuestas, fechas programadas y órdenes abiertas no reinician el año. Sin ejecuciones, se usa la fecha civil UTC del registro. Se suma un año de calendario; 29/02 pasa a 28/02 cuando corresponde, sin sustituirlo por 365 días. La evaluación usa un Clock UTC inyectable.

Programación dentro de plazo: RUTINARIO en PROGRAMADA/EN_EJECUCION con fecha desde la evaluación hasta el límite, ambos inclusive. Propuestas, terminales y otros tipos no cuentan. Ausencia de fecha, fecha pasada y fecha posterior al límite se informan por separado. Pasada y posterior pueden coincidir cuando el límite anual ya quedó atrás: son indicadores independientes y no se suman para obtener el total de órdenes. Una fecha pasada no prueba por sí sola incumplimiento anual ni falta de ejecución de una orden todavía EN_EJECUCION.

## API y consistencia

`GET /api/v1/puentes/{puenteId}/ordenes-mantenimiento/rutinario-anual?page=0&size=20` devuelve referencia/origen, fecha de evaluación, límite, cuatro conteos globales y página `ordenes` con DTO MapStruct existente. Las filas son solo rutinarias PROGRAMADA/EN_EJECUCION, por ID descendente; máximo 100 por página. Los conteos no cambian según la página; una página vacía conserva el contexto y totales. No carga todo el historial ni relaciones vecinas.

Controlador y servicio autorizan Catedrático/Admin; JWT y rol vigente los aporta P2. 400 para UUID/página/tamaño/desplazamiento inválidos; 401/403 por sesión/rol; 404 por puente ausente. No hay parámetro del cliente para sustituir la fecha de evaluación o la base. Se usa transacción de solo lectura REPEATABLE_READ para que resumen, conteos y filas provengan de la misma instantánea. No se modifica el esquema.

La consulta no crea órdenes, cambia estados/fechas, emite recordatorios ni certifica ejecución o cumplimiento completo de RN-MTO-07. La garantía anual exige todavía su mecanismo de programación efectiva y proceso diario, sin confundirlo con la alerta de inspección RN-MTO-08 ya implementada. No se añade catálogo o prioridad sugerida sin acuerdo.

## Frontend e integración

`SeguimientoRutinario` consume el GET real, muestra fechas civiles, fuente, indicadores y página de órdenes. Seleccionar una fila pide abrir el detalle; no escribe. `ConsultaOrdenes` lo incorpora opcionalmente mediante `mostrarSeguimientoRutinario`, conservando el permiso de lectura existente. La selección abre el GET de detalle real incluso si esa rutina no pertenece a la página visible del historial general. Las acciones existentes conservan sus permisos/estados; guardar fecha, iniciar o cerrar recarga el resumen confirmado por servidor.

El panel bloquea navegación/selección/recarga mientras el host escribe, cancela lecturas obsoletas y retira datos ante pérdida de acceso. No hay ruta pública ni sesión alternativa. Inventario P3 disponible; acceso privado frontend P2 y conexión de host siguen pendientes.

## Verificación

### Detección por lotes para el próximo proceso interno

`SeguimientoRutinarioRepository.sinProgramacion(despues, hoy)` recorre los puentes sin rutina fechada dentro de la ventana acordada, por cursor UUID ascendente y lotes de 100. Usa la misma base, tipos, estados y límites inclusivos del resumen. Una orden propuesta, sin fecha, pasada o posterior al límite no excluye al puente; basta una rutina válida para excluirlo. Puede detectar falta de programación antes del vencimiento del año, sin etiquetarla por sí sola como incumplimiento.

El método es interno y solo lee. Cinco pruebas nuevas comparan detector y resumen, bisiestos, límites y recorrido de más de cien puentes, sin duplicados ni omisiones. SQL acotado sin cargar relaciones JPA ni listados globales completos. El incremento posterior [ADR 0036](0036-recordatorios-de-programacion-rutinaria-anual.md) incorpora el job y la política de avisos aprobada por el usuario; crear propuestas automáticas conserva su decisión propia. El detector por sí solo no crea órdenes ni notificaciones.

Verificación final de esta preparación: **1110 pruebas backend**, cinco nuevas, sin fallos/errores/omisiones. Pasan compilación limpia, Spotless, PMD, empaquetado y las tres reglas JaCoCo. Sin cambios frontend; no se repiten sus controles. No se acredita CI remoto.

`SeguimientoRutinarioTest` prueba PostgreSQL con reloj controlado: JWT de lectores, rechazo de otros roles/cambio de rol/sin sesión, selección de última ejecución rutinaria, registro UTC, bisiesto y límites inclusivos, otras tipologías/estados, indicadores que coinciden, paginación/totales/orden, padre ausente, errores y ausencia de mutaciones/avisos. Frontend prueba cliente, DOM, permisos/errores, contexto, paginación real, selección y recarga tras guardar fecha. Resultados en el manual técnico y README. MTO-08 pasa a en progreso; no se declara cerrada.
