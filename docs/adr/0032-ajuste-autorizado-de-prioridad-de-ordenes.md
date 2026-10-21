# 0032. Ajuste autorizado de prioridad de órdenes

- Estado: implementado; acceso frontend integrado y revisión/entrega pendientes
- Fecha: 20/10/2026
- Trazabilidad: HU-P8-MTO-07 (ajuste manual), RN-MTO-06, RN-USR-09; ADR 0024; DT-BE-04/07/13, DT-BD-12, DT-CAL-01/02/03

## Regla y decisión del usuario

RN-MTO-06 permite modificar la prioridad justificando el cambio, pero no enumera actores ni estados editables. El usuario adoptó explícitamente la recomendación: **Catedrático/Admin solo en PROPUESTA, PROGRAMADA o EN_EJECUCION**. EJECUTADA, DESCARTADA y CANCELADA conservan su prioridad histórica. Es una decisión del usuario para resolver el vacío, no texto literal del enunciado ni una confirmación del grupo que no se recibió.

## API y dominio

`PATCH /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}/prioridad` recibe `prioridad` (ALTA/MEDIA/BAJA, obligatoria) y `justificacion`. Devuelve 200 con el DTO MapStruct persistido; no permite fijar la sugerencia ni cambiar estado, pesos, origen o demás datos.

Controlador y fachada autorizan; se comparte carga bloqueada por orden + puente y validación del origen. La fachada invoca el bean separado `AjustePrioridadOrdenService` con transacción MANDATORY después del bloqueo. El bean comprueba la ventana acordada e invoca la única regla interna `ajustarPrioridad` de ADR 0024. No se agregan setters ni se modifica la máquina.

Todo cambio real exige justificación no blanca, incluso al regresar a la sugerida. La sugerencia queda intacta, también si es null. Repetir la prioridad vigente devuelve lo guardado sin editar la justificación anterior ni actualizado_en y sin nueva auditoría. Es un ajuste de prioridad, no edición libre de la razón de una decisión previa. El DTO no obliga a justificar un no-op, pero la UI evita enviarlo.

Sin sesión: 401; rol indebido: 403; UUID/enum/prioridad obligatoria inválidos: 400; orden ajena/ausente: 404; orden terminal (también no-op): 409; cambio sin justificación u origen inconsistente: 422. OpenAPI y Problem Details documentan las respuestas. No se crea ni calcula una sugerencia automática.

## Auditoría y concurrencia

El aspecto de P2 registra MODIFICAR solo cuando el bean devuelve true (`condicion = #result`); las instantáneas anterior/nueva incluyen prioridad, sugerida y justificacion_prioridad saneadas. La justificación de la orden es TEXT: no se copia a la columna auxiliar auditoria.motivo VARCHAR(500), no se trunca ni se inventa un máximo. Si falla la bitácora, se revierten prioridad/justificación.

El mismo bloqueo serializa ajustes y otras decisiones de la orden. Dos peticiones al mismo valor generan un único cambio/auditoría; la segunda devuelve el valor vigente. Dos ajustes distintos pueden confirmarse sucesivamente y forman una cadena coherente anterior → nuevo, con ambos actores. No se introduce un contrato de versión esperada; la API devuelve el resultado de cada operación y la UI reconcilia conflictos/resultados inciertos mediante GET.

## Límites y verificación

No hay migraciones, fórmula de sugerencia, umbrales nuevos, cancelación, cambios de programación, notificación o cola offline de mantenimiento. Faltan fórmula/insumos de prioridad sugerida y explicarlos en la vista, además de acceso P2 y conexión con inventario P3. MTO-07 permanece en progreso.

`AjustePrioridadOrdenTest`: 30 casos con PostgreSQL real, sin transacción implícita de prueba. Roles HTTP/Java, tres estados abiertos/terminales, datos y justificación inválidos, padre/origen, ausencia de sugerencia, texto largo/Unicode, conservación de datos, repetición sin cambios/bitácora, regreso a sugerida, rollback y ajustes concurrentes iguales/distintos. La suite completa aprobó **939 pruebas**, Spotless, PMD, empaquetado y las tres reglas JaCoCo; núcleo IC/máquina mantiene 100 % en sus clases configuradas. No se editan generados ni se versionan reportes de target. Frontend y prueba Postman se describen en el [manual técnico](../manual-tecnico-p8.md).
