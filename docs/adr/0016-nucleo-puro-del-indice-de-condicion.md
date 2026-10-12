# 0016. Núcleo puro del Índice de Condición

- Estado: propuesto para revisión del equipo; implementado en `feat/IC-base`
- Fecha: 09/10/2026
- Directriz o regla relacionada: RN-IC-01, RN-IC-02, RN-IC-04, DT-ARQ-06, DT-CAL-01, DT-CAL-09
- Historias: HU-P8-IC-01, primera parte (fórmula con entradas normalizadas); HU-P8-IC-02, clasificación backend
- Ampliación: 12/10/2026, clasificación del resultado sin anulaciones

## Contexto

El IC debe calcularse en el backend, con una clase pura y pruebas propias. El esquema y los metadatos de daños de P4, los pesos calibrados con acta y la integración de publicación de P5 siguen pendientes. La fórmula puede construirse y verificarse con calificaciones y pesos de prueba.

## Decisión propuesta e implementación

- `indice/CalificacionElemento` es un registro inmutable con calificación entera de 0 a 4 y peso `BigDecimal` estrictamente positivo.
- `indice/CalculadoraIndiceCondicion.calcular(List<CalificacionElemento>)` devuelve un `BigDecimal`. No usa Spring, entidades, repositorios ni estado de publicación.
- `clasificar(BigDecimal)` devuelve el enum existente `EstadoCondicion`: `BUENO` si IC ≥ 75, `REGULAR` si 50 ≤ IC < 75 y `MALO` si IC < 50 (RN-IC-04). Compara el valor recibido sin redondeo visual y conserva el IC numérico. La llamada debe recibir el resultado de `calcular`, antes de convertirlo para presentación o almacenamiento.
- Para clasificar se exige IC presente y en 0–100; de lo contrario se lanza `IllegalArgumentException`. `null` no equivale a un puente evaluado. La representación Sin evaluar pertenece a la integración con inventario y la ausencia de inspección publicada; no se añade un estado persistido nuevo.
- Se aplica RN-IC-02: `100 × (1 − Σ(calificación × peso) / (4 × Σ peso))`. La implementación usa la forma equivalente `100 × (4 × Σ peso − Σ(calificación × peso)) / (4 × Σ peso)`.
- Las sumas y productos son exactos. Solo la división final usa `MathContext.DECIMAL128`: 34 dígitos significativos y redondeo `HALF_EVEN`. Es una convención técnica de este núcleo, sujeta a revisión; no es una precisión de almacenamiento aprobada ni una calibración académica.
- Una lista nula o vacía, un elemento nulo, una calificación fuera de escala o un peso nulo/no positivo lanza `IllegalArgumentException`. No se sustituyen entradas ausentes por daños cero. Los pesos positivos y la lista no vacía aseguran un denominador positivo.
- JaCoCo exige 100 % de líneas y ramas por cada una de estas dos clases en la ejecución `check-indice`, además de conservar la regla existente del 70 % para servicios.

## Uso con datos de prueba

```java
var elementos = List.of(
    new CalificacionElemento(2, new BigDecimal("3")),
    new CalificacionElemento(0, new BigDecimal("1")),
    new CalificacionElemento(4, new BigDecimal("2")));

var calculadora = new CalculadoraIndiceCondicion();
BigDecimal ic = calculadora.calcular(elementos);
// 41.66666666666666666666666666666667
EstadoCondicion estado = calculadora.clasificar(ic);
// MALO, conservando el IC calculado
```

Los pesos de este ejemplo son fixtures de la HU; no constituyen pesos oficiales. El adaptador de P4 deberá entregar solo calificaciones de daño y sus pesos correspondientes, sin incluir dimensiones, materiales u otros campos permanentes. La calculadora recibe pares ya normalizados: no interpreta JSON ni selecciona elementos o versiones.

## Verificación

Desde la raíz de `pf-puentes-backend`, con Java 25 y sin Docker:

```bash
./mvnw -B -ntp -Dtest=CalificacionElementoTest,CalculadoraIndiceCondicionTest test jacoco:report jacoco:check@check-indice
```

Resultado del 09/10/2026: 37 casos exitosos y 100 % de líneas y ramas en ambas clases. Se prueban extremos sanos/daño máximo, toda la escala 0–4, ponderación y pesos decimales, invariancia ante orden/escala, aumento del daño y entradas inválidas. El reporte queda en `target/site/jacoco/index.html`. El comando focalizado ejecuta explícitamente la regla de cobertura del núcleo; `verify` ejecuta también la regla general de servicios y la suite de integración que necesita Docker.

También pasaron `./mvnw -B -ntp spotless:check package pmd:check -DskipTests` y `./mvnw -B -ntp verify`: 78 casos en toda la suite, sin fallos, errores ni omisiones, con PostgreSQL/PostGIS y MinIO mediante Testcontainers. Ambas reglas de cobertura se cumplieron.

## Consecuencias y trabajo pendiente

Verificación del 12/10/2026: `./mvnw -B -ntp spotless:check verify pmd:check` pasó con 236 casos en toda la suite (68 del núcleo IC), sin fallos, errores ni omisiones. La clasificación añade pruebas de valores exactos, inferiores y superiores a 50/75, escalas decimales equivalentes, entradas inválidas y cálculo ponderado seguido de clasificación conservando el número original. Las dos clases del núcleo mantienen 100 % de líneas y ramas, y ambas reglas JaCoCo se cumplieron.

Esta entrega implementa la fórmula y la clasificación backend de HU-P8-IC-02; no completa las HU integradas. El componente frontend, Sin evaluar e integración con P3 siguen pendientes. HU-P8-IC-03 añadirá las cuatro anulaciones conservando el número calculado. Posteriormente se acordará con P5 la fachada del contrato 3 y con P4 la adaptación del esquema, pesos/versiones y tratamiento de elementos no aplicables/incompletos o múltiples daños por elemento.

La integración deberá decidir el redondeo de persistencia y su relación con los umbrales de estado. No se añaden endpoints, migraciones ni cambios de frontend en este paso.
