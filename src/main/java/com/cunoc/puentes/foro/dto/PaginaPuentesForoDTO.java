package com.cunoc.puentes.foro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de puentes con acceso a su foro permanente")
public record PaginaPuentesForoDTO(
    List<PuenteForoDTO> contenido,
    int pagina,
    int tamano,
    @JsonProperty("total_elementos") long totalElementos,
    @JsonProperty("total_paginas") int totalPaginas) {}
