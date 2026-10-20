package com.cunoc.puentes.notificacion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Conteo de notificaciones no leídas")
public record ConteoNoLeidasDTO(
    @Schema(description = "Total de notificaciones no leídas del usuario") long total) {}
