package com.cunoc.puentes.curso;

import java.util.Optional;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AuditoriaCursoRepository {
  private final JdbcClient jdbc;

  AuditoriaCursoRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<String> cursoAnterior(UUID cursoId, UUID usuarioId) {
    return jdbc.sql(
            """
        select jsonb_build_object('id', id, 'anio', anio, 'semestre', semestre,
          'catedratico_id', catedratico_id, 'vigente', vigente)::text
        from curso where id = :id and catedratico_id = :usuario for update
        """)
        .param("id", cursoId)
        .param("usuario", usuarioId)
        .query(String.class)
        .optional();
  }

  void registrar(
      UUID usuarioId,
      String accion,
      String entidad,
      UUID entidadId,
      String anterior,
      String nuevo) {
    jdbc.sql(
            """
        insert into auditoria(id, usuario_id, accion, entidad, entidad_id, valor_anterior, valor_nuevo)
        values (:id, :usuario, :accion, :entidad, :entidadId, cast(:anterior as jsonb), cast(:nuevo as jsonb))
        """)
        .param("id", UuidVersion7Strategy.INSTANCE.generateUuid(null))
        .param("usuario", usuarioId)
        .param("accion", accion)
        .param("entidad", entidad)
        .param("entidadId", entidadId)
        .param("anterior", anterior)
        .param("nuevo", nuevo)
        .update();
  }
}
