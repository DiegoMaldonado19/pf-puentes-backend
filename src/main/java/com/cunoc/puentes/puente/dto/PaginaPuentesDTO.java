package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página del inventario de puentes")
public record PaginaPuentesDTO(
    List<PuenteDTO> contenido,
    @Schema(description = "Número de página (base 0)") int pagina,
    int tamano,
    @JsonProperty("total_elementos") long totalElementos,
    @JsonProperty("total_paginas") int totalPaginas) {}
