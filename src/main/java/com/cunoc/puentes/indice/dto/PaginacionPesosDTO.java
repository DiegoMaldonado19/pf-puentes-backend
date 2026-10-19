package com.cunoc.puentes.indice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Página desde cero; los límites inválidos se rechazan en lugar de corregirlos silenciosamente. */
public record PaginacionPesosDTO(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {

  public PaginacionPesosDTO {
    page = page == null ? 0 : page;
    size = size == null ? 20 : size;
  }
}
