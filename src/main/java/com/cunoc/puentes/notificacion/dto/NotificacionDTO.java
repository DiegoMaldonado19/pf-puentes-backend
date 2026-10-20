package com.cunoc.puentes.notificacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Notificación de usuario en la aplicación")
public record NotificacionDTO(
    @Schema(description = "Identificador único (UUID v7)") UUID id,
    @Schema(description = "Tipo u origen del aviso") String tipo,
    @Schema(description = "Texto visible para el usuario") String mensaje,
    @Schema(description = "Ruta interna de la SPA (nullable)") String enlace,
    @JsonProperty("creado_en") @Schema(description = "Fecha de creación en UTC") Instant creadoEn,
    @JsonProperty("leida_en") @Schema(description = "Fecha de lectura en UTC (nullable)")
        Instant leidaEn) {}
