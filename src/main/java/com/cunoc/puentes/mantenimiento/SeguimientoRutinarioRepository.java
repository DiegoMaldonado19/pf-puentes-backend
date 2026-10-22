package com.cunoc.puentes.mantenimiento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas agregadas sin cargar el historial completo ni relaciones de módulos vecinos. */
@Repository
class SeguimientoRutinarioRepository {
  static final int TAMANO_LOTE = 100;
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

  /**
   * Mismo criterio que el resumen anual; cursor estable para el proceso interno, sin enviar avisos.
   */
  List<UUID> sinProgramacion(UUID despues, LocalDate hoy) {
    return jdbc.queryForList(
        """
        SELECT p.id FROM puente p
        LEFT JOIN LATERAL (
          SELECT max(fecha_ejecucion) AS ejecucion FROM orden_mantenimiento
          WHERE puente_id = p.id AND tipo = 'RUTINARIO' AND estado = 'EJECUTADA'
        ) e ON true
        WHERE (?::uuid IS NULL OR p.id > ?::uuid)
          AND NOT EXISTS (
            SELECT 1 FROM orden_mantenimiento o
            WHERE o.puente_id = p.id AND o.tipo = 'RUTINARIO'
              AND o.estado IN ('PROGRAMADA', 'EN_EJECUCION')
              AND o.fecha_programada >= ?
              AND o.fecha_programada <=
                (coalesce(e.ejecucion, (p.creado_en AT TIME ZONE 'UTC')::date)
                  + interval '1 year')::date
          )
        ORDER BY p.id LIMIT ?
        """,
        UUID.class,
        despues,
        despues,
        hoy,
        TAMANO_LOTE);
  }

  record Base(LocalDate registro, LocalDate ejecucion) {}

  // Pasadas y posteriores pueden coincidir si el límite anual ya quedó atrás.
  record Conteos(long enPlazo, long sinFecha, long pasadas, long posteriores) {}
}
