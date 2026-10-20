package com.cunoc.puentes.indice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;

/** Contrato de página estable, sin serializar la implementación interna de Spring Data. */
public record PaginaPesosDTO<T>(
    List<T> content,
    int page,
    int size,
    @JsonProperty("total_elements") long totalElements,
    @JsonProperty("total_pages") int totalPages) {

  public static <T> PaginaPesosDTO<T> de(Page<T> pagina) {
    return new PaginaPesosDTO<>(
        pagina.getContent(),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
