package com.cunoc.puentes.mantenimiento;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas agregadas sin cargar el historial completo ni relaciones de módulos vecinos. */
@Repository
class SeguimientoRutinarioRepository {
  private final JdbcTemplate jdbc;

  SeguimientoRutinarioRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Base> base(UUID puenteId) {
    return jdbc
        .query(
            """
        SELECT (p.creado_en AT TIME ZONE 'UTC')::date AS registro,
          (SELECT max(fecha_ejecucion) FROM orden_mantenimiento
           WHERE puente_id = p.id AND tipo = 'RUTINARIO' AND estado = 'EJECUTADA') AS ejecucion
        FROM puente p WHERE p.id = ?
        """,
            (rs, n) ->
                new Base(
                    rs.getObject("registro", LocalDate.class),
                    rs.getObject("ejecucion", LocalDate.class)),
            puenteId)
        .stream()
        .findFirst();
  }

  Conteos contar(UUID puenteId, LocalDate hoy, LocalDate limite) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FILTER (WHERE fecha_programada >= ? AND fecha_programada <= ?) AS en_plazo,
          count(*) FILTER (WHERE fecha_programada IS NULL) AS sin_fecha,
          count(*) FILTER (WHERE fecha_programada < ?) AS pasadas,
          count(*) FILTER (WHERE fecha_programada > ?) AS posteriores
        FROM orden_mantenimiento WHERE puente_id = ? AND tipo = 'RUTINARIO'
          AND estado IN ('PROGRAMADA', 'EN_EJECUCION')
        """,
        (rs, n) ->
            new Conteos(
                rs.getLong("en_plazo"),
                rs.getLong("sin_fecha"),
                rs.getLong("pasadas"),
                rs.getLong("posteriores")),
        hoy,
        limite,
        hoy,
        limite,
        puenteId);
  }

  record Base(LocalDate registro, LocalDate ejecucion) {}

  // Pasadas y posteriores pueden coincidir si el límite anual ya quedó atrás.
  record Conteos(long enPlazo, long sinFecha, long pasadas, long posteriores) {}
}
