package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record RevisionAbiertaDTO(
    UUID id,
    @JsonProperty("inspeccion_id") UUID inspeccionId,
    @JsonProperty("revisor_id") UUID revisorId,
    @JsonProperty("creado_en") Instant creadoEn) {
  static RevisionAbiertaDTO de(Revision revision) {
    return new RevisionAbiertaDTO(
        revision.getId(),
        revision.getInspeccionId(),
        revision.getRevisorId(),
        revision.getCreadoEn());
  }
}
