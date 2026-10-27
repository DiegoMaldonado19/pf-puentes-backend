package com.cunoc.puentes.revision;

import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionRevisionDTO(
    @NotNull Veredicto veredicto,
    @Size(max = 10000) String observacion,
    @JsonProperty("estado_confirmado") EstadoCondicion estadoConfirmado,
    @JsonProperty("justificacion_estado") @Size(max = 10000) String justificacionEstado) {}
