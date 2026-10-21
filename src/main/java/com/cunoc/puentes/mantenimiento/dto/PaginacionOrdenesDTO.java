package com.cunoc.puentes.mantenimiento.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PaginacionOrdenesDTO(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {
  public PaginacionOrdenesDTO {
    page = page == null ? 0 : page;
    size = size == null ? 20 : size;
  }
}
