package com.cunoc.puentes;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Genera el diccionario de datos desde los COMMENT ON de las migraciones (Backend/03). El CI falla
 * si el archivo generado no coincide con el versionado.
 */
@DataJpaTest
class DiccionarioDeDatosTest extends PruebaIntegracion {

  private static final Path DICCIONARIO = Path.of("docs/diccionario-de-datos.md");

  private static final String COLUMNAS =
      """
      SELECT c.relname AS tabla,
        obj_description(c.oid, 'pg_class') AS descripcion_tabla,
        a.attname AS columna,
        format_type(a.atttypid, a.atttypmod) AS tipo,
        a.attnotnull AS obligatoria,
        pg_get_expr(d.adbin, d.adrelid) AS por_defecto,
        (SELECT string_agg(
            CASE k.contype WHEN 'p' THEN 'PK' ELSE 'FK → ' || k.confrelid::regclass END,
            ', ' ORDER BY k.contype DESC)
          FROM pg_constraint k
          WHERE k.conrelid = c.oid AND k.contype IN ('p', 'f') AND a.attnum = ANY (k.conkey)
        ) AS llaves,
        col_description(c.oid, a.attnum) AS descripcion
      FROM pg_class c
      JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
      LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
      WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r'
        AND c.relname NOT IN ('flyway_schema_history', 'spatial_ref_sys')
      ORDER BY c.relname, a.attnum
      """;

  private static final String RESTRICCIONES =
      """
      SELECT conrelid::regclass::text AS tabla, pg_get_constraintdef(oid) AS definicion
      FROM pg_constraint
      WHERE contype IN ('c', 'u') AND connamespace = 'public'::regnamespace
      ORDER BY contype DESC, conname
      """;

  @Autowired DataSource dataSource;

  @Test
  void generaElDiccionarioYTodaColumnaEstaComentada() throws IOException {
    JdbcClient jdbc = JdbcClient.create(dataSource);
    List<Columna> columnas = jdbc.sql(COLUMNAS).query(Columna.class).list();
    Map<String, List<String>> restricciones =
        jdbc.sql(RESTRICCIONES).query(Restriccion.class).list().stream()
            .collect(groupingBy(Restriccion::tabla, mapping(Restriccion::definicion, toList())));

    Files.writeString(DICCIONARIO, markdown(columnas, restricciones));

    assertThat(columnas)
        .filteredOn(columna -> columna.descripcionTabla() == null || columna.descripcion() == null)
        .extracting(columna -> columna.tabla() + "." + columna.columna())
        .as("Tablas o columnas sin COMMENT ON")
        .isEmpty();
  }

  private static String markdown(List<Columna> columnas, Map<String, List<String>> restricciones) {
    Map<String, List<Columna>> tablas =
        columnas.stream().collect(groupingBy(Columna::tabla, LinkedHashMap::new, toList()));
    StringBuilder md =
        new StringBuilder(
            """
            # Diccionario de datos

            > Generado por `DiccionarioDeDatosTest` desde los `COMMENT ON` de las migraciones: \
            no se edita a mano. Se actualiza al correr `./mvnw verify`.

            """);
    tablas.forEach(
        (tabla, cols) ->
            md.append(
                "- [`%s`](#%s): %s\n".formatted(tabla, tabla, cols.get(0).descripcionTabla())));
    tablas.forEach(
        (tabla, cols) -> {
          md.append("\n## %s\n\n%s\n\n".formatted(tabla, cols.get(0).descripcionTabla()))
              .append("| Columna | Tipo | Nulo | Por defecto | Llaves | Descripción |\n")
              .append("|---|---|---|---|---|---|\n");
          cols.forEach(
              columna ->
                  md.append(
                      "| `%s` | %s | %s | %s | %s | %s |\n"
                          .formatted(
                              columna.columna(),
                              celda(columna.tipo()),
                              columna.obligatoria() ? "No" : "Sí",
                              celda(columna.porDefecto()),
                              celda(columna.llaves()),
                              celda(columna.descripcion()))));
          List<String> deLaTabla = restricciones.getOrDefault(tabla, List.of());
          if (!deLaTabla.isEmpty()) {
            md.append("\n**Restricciones**\n\n");
            deLaTabla.forEach(definicion -> md.append("- `%s`\n".formatted(definicion)));
          }
        });
    return md.toString();
  }

  private static String celda(String valor) {
    return valor == null ? "" : valor.replace("|", "\\|");
  }

  record Columna(
      String tabla,
      String descripcionTabla,
      String columna,
      String tipo,
      boolean obligatoria,
      String porDefecto,
      String llaves,
      String descripcion) {}

  record Restriccion(String tabla, String definicion) {}
}
