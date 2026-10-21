package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de solicitudes de alta")
public record PaginaSolicitudesDTO(
    List<SolicitudPuenteDTO> contenido,
    int pagina,
    int tamano,
    @JsonProperty("total_elementos") long totalElementos,
    @JsonProperty("total_paginas") int totalPaginas) {}
