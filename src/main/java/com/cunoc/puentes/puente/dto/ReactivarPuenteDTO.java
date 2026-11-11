package com.cunoc.puentes.puente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reactivación de un puente dado de baja (RN-INV-08); el motivo queda en la auditoría. */
@Schema(description = "Reactivación de un puente dado de baja")
public record ReactivarPuenteDTO(
    @NotBlank
        @Size(max = 500)
        @Schema(
            description = "Por qué vuelve a estar en servicio",
            example = "La estructura fue reparada")
        String motivo) {}
