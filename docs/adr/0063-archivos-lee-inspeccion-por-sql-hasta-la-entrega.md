# 0063. Archivos lee la inspección por SQL hasta después de la entrega

- Estado: sustituido por la implementación del contrato 15 (28/10/2026)
- Fecha: 28/10/2026
- Directriz o regla relacionada: contrato 15 de `docs-analisis/Equipo/02-dependencias-y-contratos.md`; ADR 0010 (lectura cruzada por SQL); ADR 0047 y 0050–0053 (orden de comprobaciones del replay)

## Contexto
El contrato 15 pide que `archivo/` use `InspeccionService.exigirBorradorPropio`. No lo usa. Hoy `ArchivoService` lee la tabla `inspeccion` por SQL en tres puntos:
- `buscarInspeccion` (en `inspeccionPropia`): titularidad y `eliminado_en`;
- `buscarParaPurgar`: borradores vencidos para la purga de RN-ARC-06;
- `BloqueoArchivo.inspeccion`: `select ... for update` sobre la fila. `contarFotos`, que llama el envío, la toma antes de cambiar el estado.

`exigirBorradorPropio` no tiene llamadores en el código de producción. El orden de las comprobaciones (inspección con bloqueo, UUID, `yaGuardado`, editabilidad) es parte del replay idempotente de fotos, PDF, baja y envío. Cambiarlo a un día de la entrega arriesga esas pruebas.

## Decisión
Para la entrega, `archivo/` conserva su SQL. Después de la entrega, el camino de salida es:
1. `InspeccionService.exigirPropia(inspeccionId, usuarioId)`: devuelve `puenteId` o 404. Reemplaza a `inspeccionPropia` antes de `yaGuardado`. Este método todavía no existe.
2. `exigirBorradorPropio` (409) se llama después de `yaGuardado`, para que un reenvío del mismo id siga devolviendo el archivo guardado.
3. `buscarParaPurgar` y `BloqueoArchivo.inspeccion` se quedan en SQL: la purga lista archivos, y el `for update` es un bloqueo de fila, no una lectura de titularidad.

## Consecuencias
- Se gana: la entrega no depende de cambiar el orden de las comprobaciones.
- Se pierde: `archivo/` depende de columnas de `inspeccion` que el compilador no revisa. Un cambio en esa tabla se detecta al ejecutar la consulta.
- Al hacer el camino de salida, repetir las pruebas de replay de fotos, PDF, baja y envío antes de fusionar.

## Cierre del desvío
`ArchivoService` autoriza la titularidad con `InspeccionService.exigirPropia` antes
del replay y exige `exigirBorradorPropio` únicamente para una carga nueva o baja.
Un UUID ya guardado sigue siendo recuperable después del envío. La purga y los
bloqueos mantienen SQL; la purga vuelve a comprobar estado, edad y `purgado_en`
después de obtener el bloqueo, antes de eliminar objetos. Los cupos cuentan solo
archivos no purgados.
