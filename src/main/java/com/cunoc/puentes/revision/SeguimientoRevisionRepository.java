package com.cunoc.puentes.revision;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SeguimientoRevisionRepository {
  private final JdbcClient jdbc;

  SeguimientoRevisionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Contexto> contexto(UUID inspeccion, boolean bloquear) {
    return jdbc.sql(
            "select i.autor_id, i.estado, c.catedratico_id from inspeccion i left join curso c on c.id = i.curso_id where i.id = :id and i.eliminado_en is null"
                + (bloquear ? " for update of i" : ""))
        .param("id", inspeccion)
        .query(Contexto.class)
        .optional();
  }

  Optional<UUID> inspeccionDeObservacion(UUID id) {
    return jdbc.sql(
            "select r.inspeccion_id from observacion_anclada o join revision r on r.id = o.revision_id where o.id = :id")
        .param("id", id)
        .query(UUID.class)
        .optional();
  }

  boolean fueRevisor(UUID actor, UUID inspeccion) {
    return jdbc.sql(
            "select exists(select 1 from revision where inspeccion_id = :id and revisor_id = :actor)")
        .param("id", inspeccion)
        .param("actor", actor)
        .query(Boolean.class)
        .single();
  }

  Optional<UUID> inspeccionDelRevisor(UUID actor, UUID revision) {
    return jdbc.sql("select inspeccion_id from revision where id = :id and revisor_id = :actor")
        .param("id", revision)
        .param("actor", actor)
        .query(UUID.class)
        .optional();
  }

  Page<Pendiente> pendientes(UUID actor, Pageable pagina) {
    String filtro =
        """
        from observacion_anclada o join revision r on r.id = o.revision_id
        join inspeccion i on i.id = r.inspeccion_id
        join puente p on p.id = i.puente_id
        where i.autor_id = :actor and i.eliminado_en is null
          and r.emitida_en is not null and o.resuelta_en is null
        """;
    long total =
        jdbc.sql("select count(*) " + filtro).param("actor", actor).query(Long.class).single();
    var filas =
        jdbc.sql(
                "select o.id, r.id as revision_id, i.id as inspeccion_id, o.elemento_ref, o.texto, i.estado, p.codigo as puente_codigo "
                    + filtro
                    + " order by r.creado_en, o.creado_en, o.id limit :limite offset :inicio")
            .param("actor", actor)
            .param("limite", pagina.getPageSize())
            .param("inicio", pagina.getOffset())
            .query(Pendiente.class)
            .list();
    return new PageImpl<>(filas, pagina, total);
  }

  record Contexto(UUID autorId, String estado, UUID catedraticoId) {}

  Page<InspeccionRevisada> inspecciones(UUID actor, Pageable pagina) {
    String filtro =
        "from inspeccion i join puente p on p.id = i.puente_id where i.autor_id = :actor and i.eliminado_en is null and exists (select 1 from revision r where r.inspeccion_id = i.id)";
    long total =
        jdbc.sql("select count(*) " + filtro).param("actor", actor).query(Long.class).single();
    var filas =
        jdbc.sql(
                "select i.id as inspeccion_id, p.codigo as puente_codigo, i.estado "
                    + filtro
                    + " order by i.creado_en desc, i.id desc limit :limite offset :inicio")
            .param("actor", actor)
            .param("limite", pagina.getPageSize())
            .param("inicio", pagina.getOffset())
            .query(InspeccionRevisada.class)
            .list();
    return new PageImpl<>(filas, pagina, total);
  }

  record InspeccionRevisada(
      @com.fasterxml.jackson.annotation.JsonProperty("inspeccion_id") UUID inspeccionId,
      @com.fasterxml.jackson.annotation.JsonProperty("puente_codigo") String puenteCodigo,
      String estado) {}

  record Pendiente(
      UUID id,
      @com.fasterxml.jackson.annotation.JsonProperty("revision_id") UUID revisionId,
      @com.fasterxml.jackson.annotation.JsonProperty("inspeccion_id") UUID inspeccionId,
      @com.fasterxml.jackson.annotation.JsonProperty("elemento_ref") String elementoRef,
      String texto,
      String estado,
      @com.fasterxml.jackson.annotation.JsonProperty("puente_codigo") String puenteCodigo) {}
}
