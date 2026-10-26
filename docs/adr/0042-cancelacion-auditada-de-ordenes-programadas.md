# 0042 — Cancelación autorizada y auditada de órdenes programadas

- Estado: implementado
- Fecha: 25/10/2026
- Trazabilidad: HU-P8-MTO-06 (parte cancelación), RN-MTO-04, RN-MTO-09, DT-BE-07/13, DT-BD-11/12, DT-CAL-01/02/03

## Contexto y decisión

`RN-MTO-04` contempla el estado `CANCELADA` dentro del ciclo de vida de órdenes y `RN-MTO-09` exige conservar las órdenes canceladas y su motivo en el historial sin borrado físico. Siguiendo la política adoptada por el responsable P8 para la transición que el diagrama dejaba abierta:

- **Actores autorizados:** únicamente `CATEDRATICO` y `ADMINISTRADOR` con cuenta activa, validado en controlador y fachada/servicio.
- **Transición única permitida hacia `CANCELADA`:** `PROGRAMADA → CANCELADA` con motivo obligatorio no blanco. No se permite cancelar desde `PROPUESTA` (corresponde a `DESCARTADA`), desde `EN_EJECUCION` ni reabrir estados terminales (`EJECUTADA`, `DESCARTADA`, `CANCELADA`).
- **Endpoint HTTP:** `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/cancelacion` recibe JSON `{"motivo": "..."}` (`MotivoOrdenDTO`) y responde `200 OK` con `OrdenMantenimientoDTO` MapStruct en estado `CANCELADA`.

## Diseño interno y concurrencia

Se unifica la ejecución de decisiones con motivo en `DecisionConMotivoOrdenService` (`Propagation.MANDATORY`, `@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")`, `@Auditable(accion = Acciones.CAMBIAR_ESTADO, entidad = "orden_mantenimiento", entidadId = "#orden.id")`).

`OrdenMantenimientoService.cancelar(puenteId, ordenId, motivo)` carga y bloquea de forma pesimista la orden y el puente, valida que la inspección de origen opcional pertenezca al mismo puente y delega en `DecisionConMotivoOrdenService`. La máquina `MaquinaEstadosOrden` valida `actual.permite(destino)` y exige motivo no blanco tanto para `DESCARTADA` como para `CANCELADA`.

El motivo se almacena íntegro en la columna `TEXT` `orden_mantenimiento.motivo` y queda en `valor_nuevo.motivo` de la instantánea auditada sin imponer el límite de `VARCHAR(500)` de `auditoria.motivo`. Se conservan intervención, prioridades, fechas programadas y evidencias previas (en modo solo lectura); no existe borrado físico.

## Códigos de respuesta

- `200`: orden persistida en `CANCELADA` con motivo y auditoría confirmados en la misma transacción.
- `400`: UUID inválido o cuerpo/motivo ausente o en blanco.
- `401`: sin sesión o token inválido.
- `403`: rol sin permiso (`ESTUDIANTE` o `PROFESIONAL_EXTERNO`).
- `404`: puente inexistente u orden ausente/perteneciente a otro puente.
- `409`: orden fuera de `PROGRAMADA` o intento repetido/concurrente (p. ej. carrera entre `cancelar` e `iniciarEjecucion`), sin duplicar auditoría.
- `422`: inspección de origen incompatible con el puente o motivo en blanco en invocación Java directa.

## Verificación

`CancelacionOrdenTest` y `DecisionConMotivoOrdenTest` verifican con PostgreSQL real (sin transacción implícita de prueba) permisos HTTP y de servicio, `401/403/404/409/422`, conservación de datos y motivo largo/Unicode, consulta en historial, rollback ante fallo de auditoría y carreras concurrentes (`cancelar` vs `cancelar` y `cancelar` vs `iniciarEjecucion`). `MaquinaEstadosOrdenTest` y `OrdenMantenimientoTest` cubren el 100 % de líneas y ramas de la máquina y entidad.

