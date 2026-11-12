# 0069. Alta y edición de órdenes, y listado global de órdenes

- Estado: aceptado
- Fecha: 11/11/2026
- Revisa: ADR 0022 (persistencia y consulta base de órdenes)

## Contexto
Las órdenes solo nacían de las propuestas generadas al publicar una inspección. No había
forma de registrar una intervención que no vino de una inspección, ni de corregir una orden
abierta. Tampoco existía un listado para ver las órdenes de todos los puentes a la vez.

## Decisión
- `POST /api/v1/puentes/{puenteId}/ordenes-mantenimiento` crea una orden en estado
  `PROPUESTA`, con `tipo`, `descripcion`, `elemento_ref` opcional, `prioridad` e
  `inspeccion_id` opcional.
- `PATCH /api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}` edita `tipo` y
  `descripcion` mientras la orden no esté cerrada (`EJECUTADA`, `DESCARTADA` o `CANCELADA`).
  Las transiciones de estado siguen en `MaquinaEstadosOrden`, sin cambios.
- `GET /api/v1/ordenes-mantenimiento` lista las órdenes de todos los puentes. Filtros
  opcionales: `estado`, `tipo`, `prioridad` y `q`, que busca en la descripción, el nombre y el
  código del puente. Orden: más recientes primero; `size` máximo 100.
- El listado cruza con `puente` por SQL (ADR 0010), no por entidad JPA. Cada valor de
  filtro viaja como parámetro; el texto libre escapa `%`, `_` y `\` para `ILIKE`.
- La propuesta rutinaria anual se asegura al publicar una inspección
  (`PropuestasInspeccionPublicadaListener`), que antes nunca la invocaba.

## Consecuencias
- No se añaden ramas a `OrdenMantenimiento` ni a `MaquinaEstadosOrden`; la cobertura del 100 %
  exigida para esas clases se mantiene.
- Las pruebas del listado usan `JdbcTemplate` simulado para la paginación, los filtros y el
  escape; el SQL real se comprueba contra PostgreSQL en la verificación en vivo.
