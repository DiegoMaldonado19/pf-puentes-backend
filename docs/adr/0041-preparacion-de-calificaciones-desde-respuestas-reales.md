# 0041 — Preparación de calificaciones desde respuestas reales

Fecha: 25/10/2026. Estado: implementado para el incremento autorizado por el responsable P8. Trazabilidad: RN-IC-02/03/08 y RN-FRM-05. La detección de condiciones críticas de RN-IC-05 y la publicación P5 conservan sus contratos pendientes.

## Contexto

El núcleo IC recibe `CalificacionElemento`, pero la lectura P4 → P8 solo obtenía referencias para configurar pesos. La ruta de captura y `x-elemento-ref` son diferentes en 18 de los 19 daños de la semilla. Ya existen extracción/validación de metadatos, repositorios, MapStruct, modelos inmutables y calculadora: se amplían esas piezas sin crear otro motor de formularios ni otra fórmula.

## Decisión y contrato interno

`PreparacionCalificacionesService.preparar(formularioId, juegoId, datos)` devuelve una lista inmutable de `CalificacionElemento` ordenada por referencia. Ambos UUID son explícitos: no selecciona el formulario activo ni el juego más reciente. El consumidor debe autorizar su operación y validar el formulario completo con `FormularioValidacionService` de P4. Este adaptador valida únicamente la preparación de daños, no los datos generales, fotografías ni permisos de una inspección.

Se amplía el mismo `ExtractorElementosDeDano` para obtener referencias y rutas mediante un solo recorrido/validación. `CampoDeDano` conserva los nombres literales de propiedades como tokens inmutables y la marca de repetición. `MetadatosDanoFormulario.campos(UUID)` lee el esquema persistido sin caché; `obtener(UUID)` mantiene su contrato previo de referencias para las APIs existentes. No se interpreta una referencia como una ruta ni se dividen nombres que contienen puntos/corchetes.

Se reutilizan `VersionPesosRepository`, `PesoElementoRepository` y `PesosMapper`. La lectura interna obtiene todos los pesos con `Pageable.unpaged()`, sin trasladar el límite HTTP administrativo a un cálculo. `JuegoPesos` y `PesoConfigurado` conservan las validaciones de configuración; `CalificacionElemento` conserva la escala 0–4. El juego debe pertenecer a la misma versión y cubrir exactamente sus referencias, también si es un juego histórico parcial o si un borrador cambió de campos.

`AdaptadorRespuestasDano` recorre los tokens reales, toma solo daños y prepara sus calificaciones/pesos. Acepta números matemáticamente enteros entre 0 y 4 (incluido `2.0`); rechaza texto numérico, booleanos, fracciones, valores fuera de rango, números no finitos, estructuras incompatibles, campos ausentes y null. Cero es una respuesta válida; ausencia no se transforma en cero. Los no aplicables no se excluyen ni se ponderan por una regla inventada. Los daños bajo arreglos se rechazan incluso si no hay instancias, hasta acordar agregación/ponderación; los arreglos sin campos de daño, como `tramos` en la semilla actual, no impiden preparar los 19 daños individuales.

## Errores y límites

- Parámetros internos obligatorios: `IllegalArgumentException`.
- Formulario o juego inexistente: 404, reutilizando `formulario-no-encontrado` / `juego-pesos-no-encontrado`.
- Juego de otro formulario: 422 `juego-pesos-de-otra-version`.
- Juego inválido o cobertura incompatible: 422 `juego-pesos-incompatible`.
- Respuesta ausente/incompatible: 422 `respuesta-dano-invalida`, con `ErrorCampo` de la ruta afectada, sin devolver el contenido capturado.
- Daño repetido: 422 `dano-repetido-no-soportado`.
- Esquema incompatible: conserva 422 `metadatos-de-dano-invalidos` del extractor existente.

La operación es interna y transaccional de solo lectura. No incorpora API, pantalla, migración, auditoría de mutación, selección oficial de pesos ni escritura de inspecciones/inventario. No devuelve un estado calculado ni usa banderas críticas falsas por defecto. Para obtener un resultado oficial faltan las condiciones críticas explícitas, autorización/validación y coordinación de la publicación transaccional P5, incluida precisión persistida y conservación de varias causas. La futura publicación deberá asegurar coherencia del esquema y pesos en su propia transacción; una preparación previa no reserva ni publica un resultado.

## Verificación

Las pruebas nuevas cubren adaptación pura, rutas literales/repetición en el extractor existente e integración con PostgreSQL/PostGIS: semilla Flyway de 19 campos, más de 100 pesos, juegos explícitos/históricos, cambios de borrador, errores y ausencia de escrituras en esquema, pesos, inspecciones, puentes y auditoría. Los resultados de la ejecución se registran en el manual técnico de P8.
