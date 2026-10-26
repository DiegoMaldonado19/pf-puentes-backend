package com.cunoc.puentes.indice;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Contrato 3: resultado completo del cálculo autoritativo del IC para que P5 lo persista al
 * publicar una inspección (RN-IC-01 a RN-IC-05).
 */
public record ResultadoCalculoOficialIc(
    UUID versionPesosId,
    BigDecimal indiceCondicion,
    EstadoCondicion estadoCalculado,
    Set<Anulacion> anulaciones,
    Anulacion anulacionPrincipal) {

  public ResultadoCalculoOficialIc {
    if (versionPesosId == null || indiceCondicion == null || estadoCalculado == null) {
      throw new IllegalArgumentException(
          "El juego de pesos, el IC y el estado calculado son obligatorios.");
    }
    anulaciones = anulaciones == null ? Set.of() : Set.copyOf(anulaciones);
  }
}

