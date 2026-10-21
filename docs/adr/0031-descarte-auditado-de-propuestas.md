# 0031. Descarte autorizado y auditado de propuestas

- Estado: implementado; integración de acceso frontend y revisión/entrega pendientes
- Fecha: 20/10/2026
- Trazabilidad: HU-P8-MTO-06 (parte descarte), RN-MTO-04/09, DT-BE-07/13, DT-BD-11/12, DT-CAL-01/02/03

## Acuerdo y decisión

El usuario confirmó **Catedrático y Administrador para descartar PROPUESTA → DESCARTADA**. Es un acuerdo específico del grupo, distinto de lectura/aceptación/inicio/cierre. El origen de CANCELADA continúa pendiente; este incremento no lo decide.

`POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/descarte` recibe JSON `{"motivo":"Intervención duplicada"}` y devuelve 200 con `OrdenMantenimientoDTO` MapStruct, estado DESCARTADA y motivo persistido. El cliente no elige un estado arbitrario. El contrato se documenta en OpenAPI; los errores usan Problem Details.

Controlador y fachada autorizan ambos roles. Se comparte la carga con bloqueo pesimista por orden + puente y la validación de origen de las otras decisiones. La fachada llama a `DescarteOrdenService` separado, con transacción MANDATORY y aspecto de auditoría después del bloqueo. La máquina exige PROPUESTA y motivo no blanco antes de mutar. Se conservan datos, prioridades y fechas; no hay borrado físico ni reapertura de terminales.

Sin sesión: 401; rol indebido: 403; UUID o cuerpo/motivo ausente/vacío: 400; orden ajena/ausente: 404; estado incompatible o repetición: 409; origen inspección/puente incompatible: 422. La llamada Java también conserva la validación de motivo del dominio (422). Dos descartes o aceptación/descarte simultáneos confirman una sola decisión y una auditoría; la otra operación recibe 409. Fallar la bitácora revierte estado y motivo.

## Motivo y bitácora

`orden_mantenimiento.motivo` es TEXT y el enunciado no fija máximo. Se conserva el texto escrito sin truncarlo ni añadir un máximo de 500 por conveniencia. El motivo queda en `valor_nuevo.motivo` de la instantánea CAMBIAR_ESTADO, usando el saneamiento central de P2. No se copia a `auditoria.motivo` (VARCHAR(500), columna usada por otras operaciones), para no convertir el límite de otra columna en una nueva regla de mantenimiento ni fallar con un motivo largo. No se modifican la auditoría compartida ni migraciones existentes.

## Pull e integración

Se verificó primero el merge backend `6915578` de P3 (`origin/develop` adddcca) y frontend `514c884`: **876 backend / 242 frontend** aprobados antes de este incremento. P3 aporta catálogo/CRUD/solicitudes/bajas, campos derivados y el record `InspeccionPublicada`; no hay emisor de publicación de P5 ni metadatos de daños de P4. El inventario frontend ya usa el estado compartido de P8. La sesión/interceptores/guardias de P2 siguen pendientes; no se crean rutas públicas de mantenimiento.

## Verificación

`DescarteOrdenTest` ejecuta **33 casos** con PostgreSQL real y sin transacción implícita de prueba: roles HTTP/Java, 401, motivo inválido, estados incompatibles, padre/origen, conservación de DTO y auditoría, texto largo/Unicode sin truncar, consulta histórica y salida de propuestas pendientes, rollback, dos descartes y carrera aceptar/descartar. Fixtures exclusivos de prueba. La suite completa y controles finales constan en el [manual técnico](../manual-tecnico-p8.md#7-verificación-y-entrega).

MTO-06 sigue parcial: falta cancelación con transición/actores acordados y el host autenticado/ruta de P2 y conexión de P8 con el puente real disponible en P3, además de revisión/entrega/CI. El ajuste manual de prioridad es un incremento separado, con actores por confirmar.
