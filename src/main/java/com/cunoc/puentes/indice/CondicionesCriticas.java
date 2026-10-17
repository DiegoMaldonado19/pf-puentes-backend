package com.cunoc.puentes.indice;

import java.util.EnumSet;
import java.util.Set;

/** Banderas normalizadas de RN-IC-05; su correspondencia con el formulario requiere a P4. */
public record CondicionesCriticas(
    boolean socavacionConCimentacionExpuestaEnEstriboOPila,
    boolean asentamientoDeEstructura,
    boolean portanteCortadoOConPerdidaDeSeccion,
    boolean ausenciaTotalDePortante) {

  public Set<Anulacion> anulaciones() {
    EnumSet<Anulacion> causas = EnumSet.noneOf(Anulacion.class);
    if (socavacionConCimentacionExpuestaEnEstriboOPila) {
      causas.add(Anulacion.SOCAVACION);
    }
    if (asentamientoDeEstructura) {
      causas.add(Anulacion.ASENTAMIENTO);
    }
    if (portanteCortadoOConPerdidaDeSeccion) {
      causas.add(Anulacion.PERDIDA_SECCION);
    }
    if (ausenciaTotalDePortante) {
      causas.add(Anulacion.AUSENCIA_PORTANTE);
    }
    return Set.copyOf(causas);
  }
}
