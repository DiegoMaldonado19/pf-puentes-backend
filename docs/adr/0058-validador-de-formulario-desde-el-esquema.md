# 0058. Validación del formulario desde su esquema, no con rutas fijas

- Estado: aceptado
- Fecha: 28/10/2026
- Directriz o regla relacionada: DT-ARQ-03, DT-BE-15, DT-DEP-09, DT-DEP-11, DT-DEP-08 y RN-INS-06

## Contexto
El puerto de P5 hacia P4 (`ConsultaFormulario`) estaba cableado a rutas fijas de `application.properties`. El esquema SIECA V1.0.0 no tiene esas rutas, así que todo envío real respondía 422.

## Decisión
El puerto lo implementa `ConsultaFormularioEsquema`, que lee permanentes (`x-permanente`), daños (`x-dano`) y grupos (`x-grupo`) del JSON Schema de la versión activa. Los obligatorios se marcan por campo, recorriendo objetos y arreglos. El tipo, el rango y los valores permitidos los valida el mismo validador de P4. Las rutas fijas se conservan solo como formulario de pruebas, en `src/test`, para las pruebas de P5 con esquema de prueba.

## Consecuencias
Las pruebas con esquema real están en `ConsultaFormularioEsquemaTest`. La validación completa conserva todos los errores de networknt, incluidos `required` dentro de `allOf` y `$ref`; el recorrido propio solo añade el detalle de las hojas ausentes para la interfaz. Solo la validación parcial descarta `required`. Los arreglos conservan sus posiciones nulas al validar, para no aceptar una copia distinta del dato guardado ni desplazar los índices de error. Las pruebas de P5 que usan un esquema `{"type":"object"}` siguen usando el formulario de pruebas (`sgp.pruebas.formulario-real=false`). Crear una versión con campos obligatorios en la raíz ya no falla: el esquema se compila, no se valida contra un objeto vacío.
