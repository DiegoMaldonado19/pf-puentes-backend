# 0014. Tabla shedlock con el esquema de la librería

- Estado: aceptado
- Fecha: 04/10/2026
- Directriz o regla relacionada: DT-BE-14, DT-BD-05, DT-BD-06, DT-BD-07

## Contexto
DT-BE-14 pide procesos programados con ShedLock para que una sola réplica ejecute cada uno. Su proveedor JDBC lee y escribe una tabla con nombres y tipos fijos: `name` como llave, `lock_until`, `locked_at` y `locked_by` en `TIMESTAMP`. Nuestras convenciones piden UUID v7 (DT-BD-05) y `creado_en`/`actualizado_en` en `TIMESTAMPTZ` (DT-BD-06).

## Decisión
- La tabla `shedlock` usa exactamente el esquema de la documentación de ShedLock para PostgreSQL, en la migración `V2026_10_04_2015`.
- `common/ProcesosProgramadosConfig` usa `usingDbTime()`: las marcas las pone PostgreSQL en UTC, no el reloj de cada réplica (DT-BD-07).
- La aplicación nunca escribe en ella; solo ShedLock.

## Consecuencias
- Es la segunda excepción a DT-BD-05, después del catálogo del INE ([0011](0011-catalogo-ine-con-clave-natural.md)).
- Cada proceso nuevo solo necesita `@Scheduled` y `@SchedulerLock(name = "...")`; no hace falta otra migración.
- Si ShedLock cambia su esquema en una versión mayor, se ajusta con una migración nueva.
