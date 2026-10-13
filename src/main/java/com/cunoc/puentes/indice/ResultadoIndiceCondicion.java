package com.cunoc.puentes.indice;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/** Resultado del núcleo puro; conserva todas las causas sin definir precedencia de persistencia. */
public record ResultadoIndiceCondicion(
    BigDecimal indiceCondicion, EstadoCondicion estadoCalculado, Set<Anulacion> anulaciones) {

  public ResultadoIndiceCondicion {
    Objects.requireNonNull(indiceCondicion, "El IC del resultado no puede ser nulo.");
    Objects.requireNonNull(estadoCalculado, "El estado calculado no puede ser nulo.");
    anulaciones = Set.copyOf(anulaciones);
  }
}
