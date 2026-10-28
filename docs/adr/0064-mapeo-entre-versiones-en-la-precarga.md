# 0064. El mapeo entre versiones se aplica en la precarga

- Estado: aceptado
- Fecha: 28/10/2026
- Directriz o regla relacionada: RN-FRM-06, RN-INS-09 (precarga de datos permanentes); ADR 0058

## Contexto
`formulario_version.mapeo` se guardaba al publicar, pero ningún código lo leía. La precarga de una inspección nueva toma los permanentes de la última inspección publicada del puente con las rutas de la versión **activa**. Si esa inspección se levantó con una versión anterior y un campo cambió de nombre, el dato se perdía en silencio.

## Decisión
- Formato del mapeo (el que ya pide la pantalla de publicación): `{"ruta.antigua": "ruta.nueva"}`. Un destino que no es texto (por ejemplo `null`) marca el campo como eliminado; los campos que no aparecen se consideran equivalentes.
- `ConsultaFormulario.traducir(datos, origen, destino)` aplica, en orden de publicación, los mapeos de las versiones publicadas después de la de origen y hasta la de destino.
- `PrecargaInspeccionService` traduce los datos de la inspección de origen antes de extraer los permanentes.

## Consecuencias
- Se gana: la precarga conserva los datos permanentes entre versiones y el mapeo deja de ser solo histórico.
- Las inspecciones publicadas no cambian: se traduce una copia al armar la precarga (RN-FRM-05).
- El mapeo no se valida contra los esquemas al publicar. Una ruta mal escrita no rompe nada: su valor simplemente no se precarga.
