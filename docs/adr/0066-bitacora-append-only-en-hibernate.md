# 0066. Bitácora append-only también en Hibernate

- Estado: aceptado
- Fecha: 28/10/2026
- Directriz: privilegios mínimos de `puentes_app`, auditoría append-only

## Decisión
`Auditoria` lleva `@Immutable`: Hibernate inserta y lee las filas, pero no emite
UPDATE por dirty checking, tampoco si un snapshot JSON numérico cambia de tipo
Java (`Long`/`Integer`). PostgreSQL conserva la prohibición de UPDATE y DELETE
para `puentes_app`; no se amplían permisos para ocultar el error.

## Verificación
`AuditoriaRepositoryTest` fuerza cambios en un JSON y un atributo después del
INSERT bajo `SET LOCAL ROLE puentes_app`; la fila original se conserva y el rol
sigue sin UPDATE. `InspeccionInicioTest` verifica el alta auditada con ese rol.
La diferencia numérica es un disparador posible, no una causa probada del
incidente original: la garantía es que ningún dirty checking actualiza bitácora.

## Consecuencias
Los cambios posteriores sobre una entidad de auditoría gestionada no se guardan.
La corrección de un hecho se registra con una nueva fila, nunca editando la anterior.
