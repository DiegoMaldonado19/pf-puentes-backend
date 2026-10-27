package com.cunoc.puentes.revision;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lectura del contexto académico persistido en la inspección, nunca inferido por puente. */
@Repository
class BandejaRevisionRepository {
  private static final String CONTEXTO =
      """
      from inspeccion i
      join curso c on c.id = i.curso_id
      join usuario u on u.id = i.autor_id
      join puente p on p.id = i.puente_id
      where c.catedratico_id = :actor and i.autor_id <> :actor
        and u.rol = 'ESTUDIANTE' and i.eliminado_en is null
        and exists (select 1 from inscripcion m where m.curso_id = c.id and m.estudiante_id = i.autor_id)
      """;
  private final JdbcClient jdbc;

  BandejaRevisionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Page<BandejaRevisionDTO> pendientes(UUID actor, String estado, Pageable pagina) {
    String filtro =
        CONTEXTO
            + " and i.estado in ('ENVIADA', 'EN_REVISION')"
            + (estado == null ? "" : " and i.estado = :estado");
    var total = jdbc.sql("select count(*) " + filtro).param("actor", actor);
    var filas =
        jdbc.sql(
                """
        select i.id as inspeccion_id, c.nombre as curso_nombre, u.nombre as autor_nombre,
          p.codigo as puente_codigo, i.estado, i.enviada_en,
          (select r.id from revision r where r.inspeccion_id = i.id and r.veredicto is null
           order by r.creado_en, r.id limit 1) as revision_id
        """
                    + filtro
                    + " order by i.enviada_en, i.id limit :limite offset :inicio")
            .param("actor", actor)
            .param("limite", pagina.getPageSize())
            .param("inicio", pagina.getOffset());
    if (estado != null) {
      total.param("estado", estado);
      filas.param("estado", estado);
    }
    return new PageImpl<>(
        filas.query(BandejaRevisionDTO.class).list(), pagina, total.query(Long.class).single());
  }

  Optional<String> bloquear(UUID actor, UUID inspeccion) {
    return jdbc.sql("select i.estado " + CONTEXTO + " and i.id = :id for update of i")
        .param("actor", actor)
        .param("id", inspeccion)
        .query(String.class)
        .optional();
  }

  Optional<String> estado(UUID actor, UUID inspeccion) {
    return jdbc.sql("select i.estado " + CONTEXTO + " and i.id = :id")
        .param("actor", actor)
        .param("id", inspeccion)
        .query(String.class)
        .optional();
  }

  Optional<UUID> inspeccionDeRevision(UUID actor, UUID revision) {
    return jdbc.sql("select inspeccion_id from revision where id = :id and revisor_id = :actor")
        .param("id", revision)
        .param("actor", actor)
        .query(UUID.class)
        .optional();
  }

  Evidencia evidencia(UUID inspeccion) {
    return jdbc.sql(
            "select f.esquema::text as esquema, i.datos::text as datos from inspeccion i join formulario_version f on f.id = i.formulario_version_id where i.id = :id")
        .param("id", inspeccion)
        .query(Evidencia.class)
        .single();
  }

  record Evidencia(String esquema, String datos) {}
}
