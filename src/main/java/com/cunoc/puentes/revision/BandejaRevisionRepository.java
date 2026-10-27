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
}
