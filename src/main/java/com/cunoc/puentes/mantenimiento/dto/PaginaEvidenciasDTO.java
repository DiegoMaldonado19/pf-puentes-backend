package com.cunoc.puentes.mantenimiento.dto;

import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record PaginaEvidenciasDTO(
    @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("orden_id") UUID ordenId,
    List<ArchivoDTO> content,
    int page,
    int size,
    @JsonProperty("total_elements") long totalElements,
    @JsonProperty("total_pages") int totalPages) {
  public static PaginaEvidenciasDTO de(UUID puente, UUID orden, Page<ArchivoDTO> pagina) {
    return new PaginaEvidenciasDTO(
        puente,
        orden,
        pagina.getContent(),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
