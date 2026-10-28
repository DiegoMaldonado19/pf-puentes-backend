package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.PuenteInspeccionableDTO;
import com.cunoc.puentes.inspeccion.puerto.RutasDatos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lecturas de puentes, cursos y formularios por SQL, sin importar sus entidades (ADR 0010). */
@Repository
class ReferenciasInspeccion {

  private final JdbcClient jdbc;

  ReferenciasInspeccion(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<PuenteVista> puente(UUID id) {
    return jdbc.sql(
            """
            select id, codigo, nombre, anio_construccion, activo
            from puente where id = :id
            """)
        .param("id", id)
        .query(PuenteVista.class)
        .optional();
  }

  /** Sección 1 del formulario, de los datos del inventario y del catálogo INE (RN-INV-03). */
  Map<String, Object> identificacion(UUID puenteId, Map<String, String> rutas) {
    Map<String, Object> inventario = identificacion(puenteId);
    Map<String, Object> datos = new LinkedHashMap<>();
    rutas.forEach(
        (referencia, ruta) -> {
          Object valor = RutasDatos.leer(inventario, referencia);
          if (valor != null) {
            RutasDatos.escribir(datos, ruta, valor);
          }
        });
    return datos;
  }

  Map<String, Object> identificacion(UUID puenteId) {
    Map<String, Object> seccion =
        jdbc.sql(
                """
                select p.codigo, p.nombre, p.ruta, d.nombre as departamento, m.nombre as municipio
                from puente p
                join municipio m on m.codigo = p.municipio_codigo
                join departamento d on d.codigo = m.departamento_codigo
                where p.id = :id
                """)
            .param("id", puenteId)
            .query(
                (rs, numero) ->
                    Map.<String, Object>of(
                        "codigo", rs.getString("codigo"),
                        "nombre", rs.getString("nombre"),
                        "ruta", rs.getString("ruta"),
                        "departamento", rs.getString("departamento"),
                        "municipio", rs.getString("municipio")))
            .single();
    return Map.<String, Object>of("identificacion", seccion);
  }

  Map<UUID, PuenteVista> puentes(Set<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql(
            """
            select id, codigo, nombre, anio_construccion, activo
            from puente where id in (:ids)
            """)
        .param("ids", ids)
        .query(PuenteVista.class)
        .list()
        .stream()
        .collect(Collectors.toMap(PuenteVista::id, Function.identity()));
  }

  Map<UUID, String> codigosFormulario(Set<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql("select id, codigo from formulario_version where id in (:ids)")
        .param("ids", ids)
        .query(CodigoFormulario.class)
        .list()
        .stream()
        .collect(Collectors.toMap(CodigoFormulario::id, CodigoFormulario::codigo));
  }

  Optional<UUID> catedraticoDeCurso(UUID cursoId) {
    if (cursoId == null) {
      return Optional.empty();
    }
    return jdbc.sql("select catedratico_id from curso where id = :id")
        .param("id", cursoId)
        .query(UUID.class)
        .optional();
  }

  Optional<UUID> asignacionVigente(UUID estudianteId, UUID puenteId) {
    return jdbc.sql(
            """
            select a.id
            from asignacion a
            join inscripcion i on i.id = a.inscripcion_id
            join curso c on c.id = i.curso_id
            where i.estudiante_id = :estudiante
              and a.puente_id = :puente
              and c.vigente
            order by a.creado_en, a.id
            limit 1
            """)
        .param("estudiante", estudianteId)
        .param("puente", puenteId)
        .query(UUID.class)
        .optional();
  }

  Page<PuenteInspeccionableDTO> inspectablesDeEstudiante(UUID estudianteId, Pageable pagina) {
    String filtro =
        """
        from asignacion a
        join inscripcion i on i.id = a.inscripcion_id
        join curso c on c.id = i.curso_id
        join puente p on p.id = a.puente_id
        where i.estudiante_id = :estudiante and c.vigente and p.activo
        """;
    long total =
        jdbc.sql("select count(*) " + filtro)
            .param("estudiante", estudianteId)
            .query(Long.class)
            .single();
    List<PuenteInspeccionableDTO> filas =
        jdbc.sql(
                """
                select p.id, p.codigo, p.nombre, c.id as curso_id, c.nombre as curso_nombre,
                  exists (
                    select 1 from inspeccion ins
                    where ins.puente_id = p.id
                      and ins.autor_id = :estudiante
                      and ins.estado = 'BORRADOR'
                      and ins.eliminado_en is null
                  ) as borrador_en_curso
                """
                    + filtro
                    + " order by p.codigo, p.id, c.id limit :limite offset :inicio")
            .param("estudiante", estudianteId)
            .param("limite", pagina.getPageSize())
            .param("inicio", pagina.getOffset())
            .query(PuenteInspeccionableDTO.class)
            .list();
    return new PageImpl<>(filas, pagina, total);
  }

  Page<PuenteInspeccionableDTO> inspectablesActivos(UUID autorId, Pageable pagina) {
    String filtro = "from puente p where p.activo";
    long total = jdbc.sql("select count(*) " + filtro).query(Long.class).single();
    List<PuenteInspeccionableDTO> filas =
        jdbc.sql(
                """
                select p.id, p.codigo, p.nombre, cast(null as uuid) as curso_id,
                  cast(null as varchar) as curso_nombre,
                  exists (
                    select 1 from inspeccion ins
                    where ins.puente_id = p.id
                      and ins.autor_id = :autor
                      and ins.estado = 'BORRADOR'
                      and ins.eliminado_en is null
                  ) as borrador_en_curso
                """
                    + filtro
                    + " order by p.codigo, p.id limit :limite offset :inicio")
            .param("autor", autorId)
            .param("limite", pagina.getPageSize())
            .param("inicio", pagina.getOffset())
            .query(PuenteInspeccionableDTO.class)
            .list();
    return new PageImpl<>(filas, pagina, total);
  }

  record PuenteVista(
      UUID id, String codigo, String nombre, Integer anioConstruccion, boolean activo) {}

  private record CodigoFormulario(UUID id, String codigo) {}
}
