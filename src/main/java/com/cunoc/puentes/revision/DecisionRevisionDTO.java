package com.cunoc.puentes.revision;

import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionRevisionDTO(
    @NotNull Veredicto veredicto,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            pattern = "[\\s\\S]*\\S[\\s\\S]*",
            description =
                "Observación obligatoria para todo veredicto, incluida APROBADA; no admite solo espacios. Su ausencia o texto en blanco produce 422.")
        @Size(max = 10000)
        String observacion,
    @JsonProperty("estado_confirmado") EstadoCondicion estadoConfirmado,
    @JsonProperty("justificacion_estado") @Size(max = 10000) String justificacionEstado) {}
