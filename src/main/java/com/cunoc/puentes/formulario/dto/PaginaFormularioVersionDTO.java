package com.cunoc.puentes.formulario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de versiones del formulario")
public record PaginaFormularioVersionDTO(
    List<FormularioVersionDTO> contenido,
    @Schema(description = "Número de página (base 0)") int pagina,
    int tamano,
    @JsonProperty("total_elementos") long totalElementos,
    @JsonProperty("total_paginas") int totalPaginas) {}
