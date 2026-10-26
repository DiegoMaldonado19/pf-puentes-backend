package com.cunoc.puentes.indice.dto;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado normalizado de la validación de confirmación de estado (RN-IC-06). */
@Schema(description = "Resultado normalizado de confirmación de estado")
public record ResultadoConfirmacionEstadoDTO(
    @JsonProperty("estado_calculado") @Schema(description = "Estado calculado automáticamente.")
        EstadoCondicion estadoCalculado,
    @JsonProperty("estado_confirmado") @Schema(description = "Estado confirmado por el evaluador.")
        EstadoCondicion estadoConfirmado,
    @JsonProperty("justificacion_estado")
        @Schema(description = "Justificación normalizada y limpia.")
        String justificacionEstado,
    @JsonProperty("anulacion") @Schema(description = "Anulación crítica informada si existe.")
        Anulacion anulacion,
    @JsonProperty("tiene_discrepancia")
        @Schema(description = "Indica si el estado confirmado difiere del calculado.")
        boolean tieneDiscrepancia,
    @JsonProperty("es_elevacion_de_anulacion_critica")
        @Schema(
            description =
                "Indica si se intentó elevar a Regular o Bueno un estado forzado a Malo por anulación.")
        boolean esElevacionDeAnulacionCritica,
    @JsonProperty("estado_efectivo")
        @Schema(
            description = "Estado efectivo que adoptará la inspección y el inventario al publicar.")
        EstadoCondicion estadoEfectivo) {}
