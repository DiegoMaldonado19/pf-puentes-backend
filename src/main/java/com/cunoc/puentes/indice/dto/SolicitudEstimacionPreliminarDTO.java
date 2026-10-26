package com.cunoc.puentes.indice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/** Solicitud de estimación de IC preliminar a partir de respuestas de formulario (RN-IC-07). */
@Schema(description = "Solicitud de cálculo preliminar del IC sin persistencia")
public record SolicitudEstimacionPreliminarDTO(
    @JsonProperty("version_pesos_id")
        @Schema(
            description =
                "Juego de pesos opcional; si no se especifica se toma el más reciente configurado para la versión.")
        UUID versionPesosId,
    @NotNull(message = "Las respuestas del formulario son obligatorias.")
        @JsonProperty("datos")
        @Schema(description = "Mapa de respuestas recolectadas del formulario de inspección.")
        Map<String, Object> datos,
    @JsonProperty("condiciones_criticas")
        @Schema(description = "Banderas opcionales de condiciones críticas estructurales.")
        CondicionesCriticasDTO condicionesCriticas) {}
