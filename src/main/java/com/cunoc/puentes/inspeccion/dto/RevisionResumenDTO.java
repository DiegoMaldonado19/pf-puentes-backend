package com.cunoc.puentes.inspeccion.dto;

import com.cunoc.puentes.inspeccion.puerto.ResumenRevision;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Última revisión emitida, para el autor: distintivo «Devuelta con cambios» y observaciones (B3).
 */
public record RevisionResumenDTO(
    @JsonProperty("ultimo_veredicto") String ultimoVeredicto,
    @JsonProperty("observaciones_pendientes") int observacionesPendientes,
    @JsonProperty("emitido_en") Instant emitidoEn) {

  public static RevisionResumenDTO de(ResumenRevision resumen) {
    return new RevisionResumenDTO(
        resumen.ultimoVeredicto(), resumen.observacionesPendientes(), resumen.emitidoEn());
  }
}
