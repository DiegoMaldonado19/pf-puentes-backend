package com.cunoc.puentes.notificacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de notificaciones")
public record PaginaNotificacionesDTO(
    @Schema(description = "Lista de notificaciones en la página actual")
        List<NotificacionDTO> contenido,
    @Schema(description = "Número de página actual (base 0)") int pagina,
    @Schema(description = "Tamaño de la página solicitada") int tamano,
    @JsonProperty("total_elementos")
        @Schema(description = "Cantidad total de notificaciones encontradas")
        long totalElementos,
    @JsonProperty("total_paginas") @Schema(description = "Cantidad total de páginas disponibles")
        int totalPaginas) {}
