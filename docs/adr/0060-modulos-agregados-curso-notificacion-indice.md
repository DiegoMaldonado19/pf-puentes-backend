# 0060. Módulos agregados fuera de DT-ARQ-03: curso, notificacion e indice

- Estado: aceptado
- Fecha: 28/10/2026
- Directriz o regla relacionada: DT-ARQ-03, DT-BE-15, DT-DEP-09, DT-DEP-11, DT-DEP-08 y RN-INS-06

## Contexto
DT-ARQ-03 lista los módulos de dominio del enunciado. Durante el proyecto se agregaron `curso/` (cursos y asignaciones), `notificacion/` y `indice/` (el servicio puro del índice de condición).

## Decisión
Se mantienen los tres paquetes como módulos de dominio propios, con el mismo patrón de capas que el resto (controlador, servicio, repositorio). `indice/` se separa de `inspeccion/` para que el cálculo del IC tenga su propio responsable y sus pruebas al 100 %.

## Consecuencias
Es un desvío documentado de DT-ARQ-03, que exige un ADR. El resto de los módulos no depende de la forma interna de estos paquetes, solo de sus puertos (`CalculoIndice`, `NotificacionService`, `ActivadorDeEstudiante`).
