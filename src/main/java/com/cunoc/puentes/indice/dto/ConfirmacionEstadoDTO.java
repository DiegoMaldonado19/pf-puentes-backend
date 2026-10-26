package com.cunoc.puentes.indice.dto;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Datos para validar la confirmación del estado de condición por el evaluador (RN-IC-06). */
@Schema(description = "Entrada para confirmar o justificar el estado de condición")
public record ConfirmacionEstadoDTO(
    @NotNull(message = "El estado calculado es obligatorio.")
        @JsonProperty("estado_calculado")
        @Schema(description = "Estado calculado automáticamente por la fórmula y anulaciones.")
        EstadoCondicion estadoCalculado,
    @NotNull(message = "El estado confirmado es obligatorio.")
        @JsonProperty("estado_confirmado")
        @Schema(description = "Estado de condición confirmado por el criterio del evaluador.")
        EstadoCondicion estadoConfirmado,
    @JsonProperty("justificacion_estado")
        @Schema(
            description =
                "Justificación por escrito; obligatoria si el estado confirmado difiere del calculado.")
        String justificacionEstado,
    @JsonProperty("anulacion") @Schema(description = "Anulación crítica de RN-IC-05 si la hubiere.")
        Anulacion anulacion) {}
