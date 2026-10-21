package com.cunoc.puentes.common.auditoria.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;

/** Misma forma JSON que el resto de listados: {@code content}, {@code total_elements}, … */
public record PaginaAuditoriaDTO(
    List<AuditoriaDTO> content,
    int number,
    int size,
    @JsonProperty("total_elements") long totalElements,
    @JsonProperty("total_pages") int totalPages) {

  public static PaginaAuditoriaDTO de(Page<AuditoriaDTO> pagina) {
    return new PaginaAuditoriaDTO(
        pagina.getContent(),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
