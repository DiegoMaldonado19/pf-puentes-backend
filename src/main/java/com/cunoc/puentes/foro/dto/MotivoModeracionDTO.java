package com.cunoc.puentes.foro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Cuerpo para acciones de moderación con motivo obligatorio.")
public record MotivoModeracionDTO(
    @Schema(
            description = "Motivo de la moderación",
            example = "Incumplimiento de normas de conducta")
        @NotBlank(message = "El motivo es obligatorio.")
        String motivo) {}
