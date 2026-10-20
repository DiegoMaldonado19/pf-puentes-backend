package com.cunoc.puentes.curso.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;

public record PaginaDTO<T>(
    List<T> content,
    int number,
    int size,
    @JsonProperty("total_elements") long totalElements,
    @JsonProperty("total_pages") int totalPages) {
  public static <T> PaginaDTO<T> de(Page<T> pagina) {
    return new PaginaDTO<>(
        pagina.getContent(),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
