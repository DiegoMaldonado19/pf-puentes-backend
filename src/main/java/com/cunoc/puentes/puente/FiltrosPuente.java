package com.cunoc.puentes.puente;

import com.cunoc.puentes.indice.EstadoCondicion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filtros combinables del listado; un valor null no filtra. */
final class FiltrosPuente {

  private FiltrosPuente() {}

  static Specification<Puente> de(
      String departamento,
      String municipio,
      EstadoFiltro estado,
      String texto,
      SituacionPuente situacion) {
    List<Specification<Puente>> filtros = new ArrayList<>();
    if (situacion != SituacionPuente.TODOS) {
      filtros.add(activo(situacion == SituacionPuente.ACTIVOS));
    }
    if (departamento != null) {
      filtros.add(departamento(departamento));
    }
    if (municipio != null) {
      filtros.add(municipio(municipio));
    }
    if (estado != null) {
      filtros.add(estado(estado));
    }
    if (texto != null && !texto.isBlank()) {
      filtros.add(texto(texto));
    }
    return Specification.allOf(filtros);
  }

  private static Specification<Puente> activo(boolean activo) {
    return (raiz, consulta, cb) -> cb.equal(raiz.get("activo"), activo);
  }

  private static Specification<Puente> departamento(String codigo) {
    return (raiz, consulta, cb) ->
        cb.equal(raiz.get("municipio").get("departamento").get("codigo"), codigo);
  }

  private static Specification<Puente> municipio(String codigo) {
    return (raiz, consulta, cb) -> cb.equal(raiz.get("municipio").get("codigo"), codigo);
  }

  private static Specification<Puente> estado(EstadoFiltro estado) {
    return (raiz, consulta, cb) ->
        estado == EstadoFiltro.SIN_EVALUAR
            ? cb.isNull(raiz.get("estadoActual"))
            : cb.equal(raiz.get("estadoActual"), EstadoCondicion.valueOf(estado.name()));
  }

  /** Búsqueda literal en nombre, código y ruta: %, _ y ! del usuario no son comodines. */
  private static Specification<Puente> texto(String texto) {
    String patron =
        "%"
            + texto
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%";
    return (raiz, consulta, cb) ->
        cb.or(
            cb.like(cb.lower(raiz.get("nombre")), patron, '!'),
            cb.like(cb.lower(raiz.get("codigo")), patron, '!'),
            cb.like(cb.lower(raiz.get("ruta")), patron, '!'));
  }
}
