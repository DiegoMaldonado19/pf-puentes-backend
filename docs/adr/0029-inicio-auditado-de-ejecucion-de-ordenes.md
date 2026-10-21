# 0029. Inicio autorizado y auditado de ejecución de órdenes

- Estado: implementado, pendiente de revisión y entrega
- Fecha: 20/10/2026
- Historia: HU-P8-MTO-04; RN-MTO-01/04; DT-BE-07, DT-SEC-03/04, DT-BD-12 y DT-CAL-01/02/03

## Contexto y acuerdo

El núcleo ya permite PROGRAMADA → EN_EJECUCION. El usuario confirmó que el grupo autoriza **Catedrático y Administrador para iniciar ejecución**, independientemente de los acuerdos previos de lectura/aceptación. El acuerdo no asigna actores de cierre, evidencia, descarte/cancelación, prioridad ni cambios de fechas.

## Decisión

Se expone `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/inicio-ejecucion`, sin cuerpo. Controlador y `OrdenMantenimientoService.iniciarEjecucion` verifican ambos roles. El cliente no elige un estado de destino. La fachada comparte con aceptación la carga por orden/puente con bloqueo `PESSIMISTIC_WRITE` y validación de origen. Una orden ausente o de otro puente produce 404; una inspección de origen incompatible, 422.

Después de bloquear, la fachada llama a `InicioEjecucionOrdenService.iniciar`, bean separado con transacción MANDATORY y aspecto `@Auditable(CAMBIAR_ESTADO)`. Así la instantánea anterior de P2 se toma después del bloqueo. La entidad usa la única puerta `cambiarEstado(EN_EJECUCION, ...)`; no hay setter, SQL de estado ni escritura manual de bitácora. Tras el flush del aspecto, MapStruct produce el DTO con la marca de actualización persistida.

Solo PROGRAMADA puede iniciar. PROPUESTA, EN_EJECUCION y terminales producen 409. Dos inicios concurrentes confirman una transición/auditoría y un rechazo 409; repetir no añade auditoría. Si falla la bitácora, se revierte el estado en la misma transacción. La auditoría conserva actor, fecha UTC y estados anterior/nuevo.

Se conservan intervención, prioridades/justificación, origen, programación, responsable, motivo y fecha de ejecución. No se añade una fecha de inicio al modelo ni se usa fecha de ejecución como inicio. RN-MTO-04 no exige una fecha programada presente ni restringe su relación temporal para este salto; no se inventan esas condiciones.

Respuestas: 200 con DTO persistido EN_EJECUCION; 400 UUID inválido; 401 sin sesión/token válido; 403 rol no autorizado; 404 orden/puente incompatibles o ausentes; 409 estado incompatible; 422 origen inconsistente. Errores mediante Problem Details y documentación OpenAPI.

## Integración y límites

La seguridad global de P2 sigue validando token y cuenta activa; no se agrega restricción por curso a mantenimiento. No hay migración, cambios del núcleo IC/máquina, nueva notificación ni conexión de evidencia. P9 se recibió y se verificó con la suite, pero el enunciado no exige notificar cada inicio.

El frontend incorpora una acción opcional al detalle existente con permiso explícito del host. Sin sesión/ruta/selección de puente integradas todavía no se acredita MTO-04 de extremo a extremo ni su entrega/revisión/CI.

## Verificación

`InicioEjecucionOrdenTest` ejecuta 22 casos con PostgreSQL real y sin transacción de prueba: roles, 401/403, UUID/404/origen, los cinco estados incompatibles, inicio sin fecha inventada, conservación del DTO, auditoría, rollback, repetición, seguridad directa del servicio y dos hilos sincronizados. Datos exclusivos en `inicio-ejecucion-ordenes.sql`, no en producción. Las 22 pruebas de aceptación siguen aprobadas tras compartir la carga bloqueada.

Verificación global con JDK 25: **755 pruebas backend, 0 fallos/errores/omisiones**. Incluye 68 de Notificaciones recibidas en el merge y 22 nuevas de inicio; no se atribuye a P8 el trabajo P9. Pasaron compilación limpia, Spotless, PMD, empaquetado y las tres reglas JaCoCo. Núcleo IC/máquina mantienen 100 % según las clases configuradas y servicios superan el mínimo del proyecto. Comando: `./mvnw -B -ntp clean spotless:apply verify pmd:check`, seguido de `spotless:check`. No se versionan reportes de `target/`.
