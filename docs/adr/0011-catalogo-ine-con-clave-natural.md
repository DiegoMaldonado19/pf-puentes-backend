# 0011. Catálogo del INE con su código como llave primaria

- Estado: aceptado
- Fecha: 04/10/2026
- Directriz o regla relacionada: DT-BD-05, DT-BD-06, RN-INV-03, RN-INV-04

## Contexto
DT-BD-05 pide UUID v7 como llave primaria para que el cliente offline pueda generar ids sin el servidor. Los departamentos y municipios no se crean en ningún cliente: son el catálogo oficial del INE y se cargan una sola vez con una migración.

## Decisión
- `departamento` y `municipio` usan su código INE (`codigo`) como llave primaria. Sus llaves foráneas se llaman `<tabla>_codigo` en vez de `<tabla>_id`.
- Un CHECK obliga a que el código del municipio empiece con el de su departamento. El puente guarda solo el municipio.

## Consecuencias
- La base de datos garantiza la congruencia entre departamento y municipio (RN-INV-04).
- El código del puente (`GT-<DEPTO>-<MUNI>-<CORRELATIVO>`) se arma sin traducir ids.
- Es la única excepción a DT-BD-05: toda tabla que se llena desde la aplicación sigue con UUID v7.
