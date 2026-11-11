package com.cunoc.puentes.mantenimiento.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PaginaOrdenesGlobalDTO(
    List<OrdenGlobalDTO> content,
    int page,
    int size,
    @JsonProperty("total_elements") long totalElements,
    @JsonProperty("total_pages") int totalPages) {}
