package com.cunoc.puentes.mantenimiento.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Identifica el puente consultado incluso cuando no hay propuestas. */
public record PaginaPropuestasDTO(
    @JsonProperty("puente_id") UUID puenteId,
    List<OrdenMantenimientoDTO> content,
    int page,
    int size,
    @JsonProperty("total_elements") long totalElements,
    @JsonProperty("total_pages") int totalPages) {

  public static PaginaPropuestasDTO de(UUID puenteId, Page<OrdenMantenimientoDTO> pagina) {
    return new PaginaPropuestasDTO(
        puenteId,
        pagina.getContent(),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
