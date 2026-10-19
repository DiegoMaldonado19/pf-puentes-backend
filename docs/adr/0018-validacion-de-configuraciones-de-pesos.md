# 0018. Validación de configuraciones de pesos

- Estado: propuesto para revisión del equipo; modelo, validaciones y repositorios implementados
- Fecha: 12/10/2026
- Renumerado el 18/10/2026 tras integrar el ADR 0017 de renovación de sesión de P2.
- Ampliación del 18/10/2026: repositorios y pruebas de persistencia con PostgreSQL/Testcontainers.
- Historia: HU-P8-IC-04, primera parte (modelo y validación)
- Reglas: RN-IC-03, RN-FRM-02/03, RN-IC-08; límites de la migración `V2026_10_04_2006__crear_pesos_ic.sql`

## Contexto

La base ya tiene `VersionPesos` y `PesoElemento`, con referencia al formulario, acta opcional, peso positivo y unicidad de elemento por juego. Antes de añadir un servicio de persistencia hace falta validar las configuraciones recibidas y evitar que PostgreSQL redondee silenciosamente un peso calibrado. Los metadatos reales de P4 y el vínculo preciso entre nuevas versiones de pesos/formulario siguen pendientes.

## Decisión

Se añaden dos registros de dominio puros, sin Spring/JPA: `PesoConfigurado(elementoRef, peso)` y `JuegoPesos(formularioVersionId, acta, pesos)`. No son DTO HTTP ni sustituyen las entidades existentes.

- Referencia de elemento presente, no en blanco, con hasta 200 caracteres, igual que `VARCHAR(200)`.
- Peso presente, positivo y hasta `999.999`, representable exactamente en `NUMERIC(6,3)`. Se aceptan ceros decimales finales (`1.2300`) y notación equivalente (`1E+2`); se rechazan valores que necesiten redondeo, incluidos `0.0001` y `1.2345`. Esta es una validación técnica propuesta de configuración, no la política de redondeo del IC.
- UUID de versión de formulario obligatorio y al menos un peso. Referencias duplicadas dentro del juego y entradas nulas se rechazan con `IllegalArgumentException`.
- Se conserva cada referencia tal como llegó, sin inventar una gramática JSON ni normalizar mayúsculas/rutas. La pertenencia al esquema y el carácter de daño deben comprobarse con P4 al integrar el servicio.
- Acta opcional: `null` identifica pesos provisionales, tal como indica el comentario de la tabla existente. Una referencia presente no puede estar en blanco ni superar los 300 caracteres de `VARCHAR(300)`. `esProvisional()` informa únicamente de la ausencia de referencia; una referencia no demuestra que el documento exista ni que su firma esté verificada.
- Longitudes contadas por caracteres Unicode, como los límites de PostgreSQL, sin contar doble los pares sustitutos de Java.
- Lista copiada e inmutable: crear una definición nueva no cambia una anterior. No se agregan operaciones de actualización o borrado de juegos históricos.

JaCoCo exige 100 % de líneas y ramas también para estos dos registros mediante `check-indice`; se conserva la regla del 70 % de servicios y la del núcleo del cálculo existente.

## Uso con fixtures

```java
var configuracion = new JuegoPesos(
    formularioVersionId,
    null, // Provisional; no es una calibración oficial.
    List.of(new PesoConfigurado("elemento-de-prueba", new BigDecimal("1.2300"))));
```

El UUID y la referencia son entradas de prueba, no un esquema oficial. La definición no selecciona automáticamente una versión activa ni genera un ID persistido.

## Consecuencias y siguiente entrega

La primera entrega de HU-P8-IC-04 validó y conservó una definición en memoria. La ampliación del 18/10 añade persistencia base con `VersionPesosRepository` y `PesoElementoRepository`, internos al paquete `indice`, sobre las entidades y tablas existentes. No requiere migraciones ni cambios del diccionario de datos.

La consulta de pesos usa el ID exacto del juego y `Pageable`; no selecciona por sí sola el juego activo o más reciente de un formulario. `@EntityGraph` carga el juego en la consulta para permitir su lectura sin N+1. El límite de página máximo de 100 se aplicará al exponer la futura API; esta entrega solo incorpora repositorios internos.

Las pruebas usan dos versiones ficticias de formulario en BORRADOR, creadas por SQL dentro de transacciones de prueba revertidas al terminar. Esto prueba las claves foráneas, no una política que autorice administrar pesos de formularios en borrador. No se interpreta el JSON Schema ni se decide el vínculo entre nuevas versiones de pesos y formulario.

Se comprueba guardar y releer IDs UUID v7, formulario, acta opcional, fechas y pesos decimales; paginación y ausencia de cargas perezosas adicionales al consultar el juego; separación de juegos del mismo formulario y de formularios diferentes; juego inexistente; rechazo de referencias duplicadas, pesos no positivos y claves foráneas inexistentes mediante restricciones reales de PostgreSQL.

Guardar dos juegos nuevos permite consultar el anterior sin reemplazarlo. Esto no impone inmutabilidad histórica en los repositorios JPA, que todavía permiten actualizaciones y borrados. La entrega posterior del mismo 18/10 añadió el servicio de creación con autorización, auditoría y una transacción de negocio, descrito en el [ADR 0019](0019-creacion-transaccional-y-auditada-de-pesos.md). No hay controlador ni pantalla nuevos. Tener una referencia de acta guardada no verifica su existencia o firma.

El servicio implementado comprueba que existe la versión y valida pertenencia de referencias frente a una lista normalizada del servidor; crea juegos nuevos con permiso de Administrador y auditoría. La extracción de esa lista desde metadatos reales de P4 sigue pendiente. Después vendrán DTO con Bean Validation, API/OpenAPI y pantalla Admin; MapStruct ya mapea las definiciones a entidades. La calibración inicial con acta sigue requiriendo al catedrático; el vínculo entre versiones no se decide en este modelo.

La prueba focalizada de los repositorios necesita Docker: `./mvnw -B -ntp -Dtest=PesosRepositoryTest test`. Se reutiliza `PruebaIntegracion` con PostgreSQL/PostGIS y MinIO del proyecto, sin H2. No existe aún una API que deba probarse desde Postman ni una pantalla que consuma estos repositorios; el servicio se verifica según el ADR 0019.

Pruebas específicas: límites positivos y precisión sin redondeo, referencias/acta Unicode, duplicados, entradas ausentes y copia inmutable entre definiciones. Ejecución focalizada:

```bash
./mvnw -B -ntp -Dtest=PesoConfiguradoTest,JuegoPesosTest test
```

Para verificar todas las reglas de cobertura y la integración del repositorio: `./mvnw -B -ntp spotless:check verify pmd:check`, con Docker disponible.

Verificación del 12/10/2026: formato, compilación, PMD y suite completa aprobados; 301 casos en total, incluidos 31 nuevos de configuración (19 de `PesoConfigurado`, 12 de `JuegoPesos`), sin fallos, errores ni omisiones. Ambos registros alcanzaron 100 % de líneas y ramas; se cumplieron las reglas JaCoCo existentes y ampliadas. Las pruebas de copia inmutable verifican las definiciones en memoria, no la futura conservación histórica en base de datos.

Verificación del 18/10/2026: `./mvnw -B -ntp spotless:check verify pmd:check` aprobado con 424 casos de toda la suite actual, incluidos 11 nuevos de `PesosRepositoryTest` y 144 del paquete `indice/` en total. Sin fallos, errores ni omisiones; ambas reglas JaCoCo cumplidas y las seis clases de cálculo/configuración conservan 100 % de líneas y ramas donde existen ramas. Los repositorios se prueban contra PostgreSQL real; la prueba no atribuye a esta capa autorización o protección histórica que aún corresponden al servicio pendiente.
