package com.cunoc.puentes.mantenimiento;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RecordatoriosRutinariosRepository {
  private final JdbcTemplate jdbc;
  private final EntityManager entidades;

  RecordatoriosRutinariosRepository(JdbcTemplate jdbc, EntityManager entidades) {
    this.jdbc = jdbc;
    this.entidades = entidades;
  }

  Optional<Puente> bloquear(UUID puenteId) {
    return jdbc
        .query(
            "SELECT codigo, nombre FROM puente WHERE id = ? FOR UPDATE",
            (rs, n) -> new Puente(rs.getString("codigo"), rs.getString("nombre")),
            puenteId)
        .stream()
        .findFirst();
  }

  List<UUID> destinatariosPendientes(UUID puenteId, LocalDate referencia) {
    return jdbc.queryForList(
        """
        SELECT u.id FROM usuario u
        WHERE u.estado = 'ACTIVO' AND u.rol IN ('CATEDRATICO', 'ADMINISTRADOR')
          AND NOT EXISTS (SELECT 1 FROM aviso_rutinario_anual a
            WHERE a.puente_id = ? AND a.usuario_id = u.id AND a.fecha_referencia = ?)
        ORDER BY u.id LIMIT 100
        """,
        UUID.class,
        puenteId,
        referencia);
  }

  void registrar(UUID puenteId, UUID usuarioId, LocalDate referencia) {
    entidades.persist(new AvisoRutinarioAnual(puenteId, usuarioId, referencia));
    entidades.flush();
  }

  record Puente(String codigo, String nombre) {}
}
