# 0017. Validación de configuraciones de pesos

- Estado: propuesto para revisión del equipo; modelo y validaciones implementados
- Fecha: 12/10/2026
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

Esta primera parte de HU-P8-IC-04 valida y conserva una definición en memoria. No guarda pesos, no protege por rol a un endpoint y no demuestra preservación de históricos en base de datos. No hay repositorios, servicio, controlador, migración ni pantalla nuevos en esta entrega.

El siguiente servicio debe comprobar que existe la versión del formulario, validar sus referencias con los metadatos acordados de P4, crear juegos sin sobrescribir los usados por inspecciones y aplicar autorización exclusiva de Administrador y auditoría al persistir. Después vendrán DTO con Bean Validation, MapStruct, API/OpenAPI y pantalla Admin. La calibración inicial con acta sigue requiriendo al catedrático; el vínculo entre versiones no se decide en este modelo.

Pruebas específicas: límites positivos y precisión sin redondeo, referencias/acta Unicode, duplicados, entradas ausentes y copia inmutable entre definiciones. Ejecución focalizada:

```bash
./mvnw -B -ntp -Dtest=PesoConfiguradoTest,JuegoPesosTest test
```

Para verificar todas las reglas de cobertura y la integración del repositorio: `./mvnw -B -ntp spotless:check verify pmd:check`, con Docker disponible.

Verificación del 12/10/2026: formato, compilación, PMD y suite completa aprobados; 301 casos en total, incluidos 31 nuevos de configuración (19 de `PesoConfigurado`, 12 de `JuegoPesos`), sin fallos, errores ni omisiones. Ambos registros alcanzaron 100 % de líneas y ramas; se cumplieron las reglas JaCoCo existentes y ampliadas. Las pruebas de copia inmutable verifican las definiciones en memoria, no la futura conservación histórica en base de datos.
