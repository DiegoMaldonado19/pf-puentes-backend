package com.cunoc.puentes.curso;

import com.cunoc.puentes.curso.dto.EstudianteElegibleDTO;
import com.cunoc.puentes.curso.dto.PuenteDisponibleDTO;
import java.time.Instant;
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

/** Lecturas de otros módulos por SQL, sin importar sus entidades (ADR 0010). */
@Repository
class ReferenciasCursoRepository {
  private final JdbcClient jdbc;

  ReferenciasCursoRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Cuenta> buscarCuenta(UUID id, boolean bloquear) {
    return jdbc.sql(
            "select rol, estado, correo_verificado_en from usuario where id = :id"
                + (bloquear ? " for update" : ""))
        .param("id", id)
        .query(Cuenta.class)
        .optional();
  }

  boolean puenteActivo(UUID id) {
    return jdbc.sql("select exists(select 1 from puente where id = :id and activo)")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  Page<EstudianteElegibleDTO> estudiantes(Pageable pagina, String busqueda) {
    String filtro =
        " from usuario where rol = 'ESTUDIANTE' and estado in ('ACTIVO', 'PENDIENTE')"
            + " and correo_verificado_en is not null and nombre ilike :busqueda escape '!'";
    String patron = patron(busqueda);
    long total =
        jdbc.sql("select count(*)" + filtro).param("busqueda", patron).query(Long.class).single();
    var filas =
        jdbc.sql(
                "select id, nombre, estado"
                    + filtro
                    + " order by nombre, id limit :limite offset :inicio")
            .param("limite", pagina.getPageSize())
            .param("busqueda", patron)
            .param("inicio", pagina.getOffset())
            .query(EstudianteElegibleDTO.class)
            .list();
    return new PageImpl<>(filas, pagina, total);
  }

  Page<PuenteDisponibleDTO> puentes(Pageable pagina, String busqueda) {
    String filtro =
        " from puente where activo and (nombre ilike :busqueda escape '!' or codigo ilike :busqueda escape '!')";
    String patron = patron(busqueda);
    long total =
        jdbc.sql("select count(*)" + filtro).param("busqueda", patron).query(Long.class).single();
    var filas =
        jdbc.sql(
                "select id, codigo, nombre"
                    + filtro
                    + " order by codigo, id limit :limite offset :inicio")
            .param("busqueda", patron)
            .param("limite", pagina.getPageSize())
            .param("inicio", pagina.getOffset())
            .query(PuenteDisponibleDTO.class)
            .list();
    return new PageImpl<>(filas, pagina, total);
  }

  record Cuenta(String rol, String estado, Instant correoVerificadoEn) {}

  Map<UUID, String> nombresEstudiantes(Set<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return jdbc
        .sql("select id, nombre from usuario where id in (:ids) and rol = 'ESTUDIANTE'")
        .param("ids", ids)
        .query(NombreEstudiante.class)
        .list()
        .stream()
        .collect(Collectors.toMap(NombreEstudiante::id, NombreEstudiante::nombre));
  }

  Map<UUID, PuenteDisponibleDTO> datosPuentes(Set<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return jdbc
        .sql("select id, codigo, nombre from puente where id in (:ids)")
        .param("ids", ids)
        .query(PuenteDisponibleDTO.class)
        .list()
        .stream()
        .collect(Collectors.toMap(PuenteDisponibleDTO::id, Function.identity()));
  }

  private static String patron(String texto) {
    return "%" + texto.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  record NombreEstudiante(UUID id, String nombre) {}
}
