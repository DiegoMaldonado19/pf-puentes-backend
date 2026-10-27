package com.cunoc.puentes.foro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de hilos del foro")
public record PaginaHilosDTO(
    @Schema(description = "Lista de hilos en la página actual") List<HiloDTO> contenido,
    @Schema(description = "Número de página actual (base 0)") int pagina,
    @Schema(description = "Tamaño de la página solicitada") int tamano,
    @JsonProperty("total_elementos") @Schema(description = "Cantidad total de hilos encontrados")
        long totalElementos,
    @JsonProperty("total_paginas") @Schema(description = "Cantidad total de páginas disponibles")
        int totalPaginas) {}
