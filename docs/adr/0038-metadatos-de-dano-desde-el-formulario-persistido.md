# 0038 — Metadatos de daño desde el formulario persistido

Fecha: 24/10/2026. Estado: aceptado para este incremento P8. Trazabilidad: RN-IC-02/03, RN-FRM-03/05/06. Las condiciones de RN-IC-05 conservan su integración pendiente.

## Contexto

P4 entregó versiones y JSON Schema reales. `ConfiguracionPesosService` recibe un conjunto normalizado de referencias permitidas, pero faltaba obtenerlo del servidor. La semilla V1.0.0 contiene 19 campos de daño y 18 rutas de datos distintas de su referencia semántica. Leer una lista permitida enviada por el cliente permitiría configurar campos ajenos al esquema.

## Decisión

`MetadatosDanoFormulario.obtener(UUID)` consulta únicamente el esquema persistido de esa versión mediante JDBC y delega en `ExtractorElementosDeDano`. No depende de la entidad interna P4, no sustituye una versión histórica por la activa y no usa caché porque los borradores son mutables.

El extractor recorre `properties` e `items`; toma `x-elemento-ref` de los campos con `x-dano: true` y exige integer, minimum 0, maximum 4, campo individual no permanente y referencia no vacía, única y de hasta 200 caracteres. Devuelve `ElementosDeDano` inmutable. Un arreglo aporta definiciones, no respuestas ni pesos por instancia. La ruta recorrida solo sirve para diagnosticar errores; no se convierte en referencia ni viceversa.

Versión inexistente produce 404. Metadatos incompatibles producen 422 `metadatos-de-dano-invalidos`. Se rechazan referencias/composiciones/condicionales y propiedades dinámicas que este extractor no interpreta, evitando autorizar un subconjunto silenciosamente incompleto. No es un validador general de JSON Schema: P4 conserva la validación del formulario y respuestas.

Es un componente interno de lectura transaccional. La autorización corresponde a la operación consumidora; la creación existente sigue restringida a Administrador. No se agrega HTTP, escritura, auditoría de lectura, migración ni configuración de pesos de demostración.

## Límites y continuación

La API de creación futura obtendrá este conjunto en servidor antes de llamar al servicio de pesos. Quedan por acordar completitud y relación entre juegos/versiones. El marcador `x-anulacion-critica` no establece por sí solo cuándo se cumple una condición: no se infieren umbrales universales. La adaptación de respuestas, repetidos/no aplicables y publicación P5 se implementará por separado.

## Verificación

30 casos del extractor y seis de integración aprobados con PostgreSQL/PostGIS y MinIO reales. Incluyen los 19 campos cargados por Flyway, aislamiento entre versiones, cambios de borrador sin caché, históricos, errores y ausencia de escrituras/auditoría. La integración demuestra creación interna de pesos con referencia canónica obtenida del adaptador y rechazo de la ruta física como referencia de peso. Los resultados globales y de navegador se registran en el manual técnico de P8.
