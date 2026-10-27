package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DetalleRevisionDTO(
    UUID id,
    @JsonProperty("inspeccion_id") UUID inspeccionId,
    String estado,
    Veredicto veredicto,
    String observacion,
    Map<String, Object> esquema,
    Map<String, Object> datos,
    List<CampoRevision> campos,
    List<ObservacionRevisionDTO> observaciones) {
  public record CampoRevision(String referencia, String titulo, Object valor) {}
}
