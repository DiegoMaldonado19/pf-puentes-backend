package com.cunoc.puentes.mantenimiento;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AlertasInspeccionRepository {
  static final int LOTE = 100;
  private static final String REFERENCIA =
      """
      FROM puente p
      LEFT JOIN LATERAL (
        SELECT id, publicada_en FROM inspeccion
        WHERE puente_id = p.id AND estado = 'PUBLICADA'
        ORDER BY publicada_en DESC, id DESC LIMIT 1
      ) i ON true
      """;
  // Calendario UTC explícito: no depende del TimeZone de la conexión ni equivale a 365 días.
  private static final String VENCIDA =
      """
      ((coalesce(i.publicada_en, p.creado_en) AT TIME ZONE 'UTC'
        + interval '12 months') AT TIME ZONE 'UTC') < ?
      """;
  private final JdbcTemplate jdbc;
  private final EntityManager entidades;

  AlertasInspeccionRepository(JdbcTemplate jdbc, EntityManager entidades) {
    this.jdbc = jdbc;
    this.entidades = entidades;
  }

  List<UUID> siguientes(UUID despues, Instant ahora) {
    return jdbc.queryForList(
        "SELECT p.id "
            + REFERENCIA
            + " WHERE (?::uuid IS NULL OR p.id > ?::uuid) AND "
            + VENCIDA
            + " ORDER BY p.id LIMIT "
            + LOTE,
        UUID.class,
        despues,
        despues,
        Timestamp.from(ahora));
  }

  boolean bloquear(UUID puenteId) {
    return !jdbc.queryForList("SELECT id FROM puente WHERE id = ? FOR UPDATE", UUID.class, puenteId)
        .isEmpty();
  }

  Referencia referenciaVencida(UUID puenteId, Instant ahora) {
    var filas =
        jdbc.query(
            "SELECT i.id, p.codigo, p.nombre, coalesce(i.publicada_en, p.creado_en) AS referencia "
                + REFERENCIA
                + " WHERE p.id = ? AND "
                + VENCIDA,
            (rs, n) ->
                new Referencia(
                    rs.getObject("id", UUID.class),
                    rs.getTimestamp("referencia").toInstant(),
                    rs.getString("codigo"),
                    rs.getString("nombre")),
            puenteId,
            Timestamp.from(ahora));
    return filas.isEmpty() ? null : filas.getFirst();
  }

  List<UUID> destinatariosPendientes(UUID puenteId, UUID inspeccionId) {
    return jdbc.queryForList(
        """
        SELECT u.id FROM usuario u
        WHERE u.estado = 'ACTIVO' AND u.rol IN ('CATEDRATICO', 'ADMINISTRADOR')
        AND NOT EXISTS (SELECT 1 FROM aviso_inspeccion_vencida a
          WHERE a.puente_id = ? AND a.usuario_id = u.id
          AND a.inspeccion_id IS NOT DISTINCT FROM ?::uuid)
        ORDER BY u.id LIMIT 100
        """,
        UUID.class,
        puenteId,
        inspeccionId);
  }

  void registrar(UUID puenteId, UUID usuarioId, Referencia referencia) {
    entidades.persist(
        new AvisoInspeccionVencida(
            puenteId, usuarioId, referencia.inspeccionId(), referencia.fecha()));
    entidades.flush();
  }

  record Referencia(UUID inspeccionId, Instant fecha, String codigo, String nombre) {}
}
