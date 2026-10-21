package com.cunoc.puentes.puente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Rechazo de una solicitud de alta (RN-INV-07)")
public record RechazarSolicitudDTO(
    @NotBlank @Size(max = 500) @Schema(example = "Ya existe en el inventario como GT-09-0901-0001")
        String motivo) {}
