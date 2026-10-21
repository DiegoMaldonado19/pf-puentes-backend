package com.cunoc.puentes.puente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Baja lógica de un puente (RN-INV-08)")
public record DarDeBajaPuenteDTO(
    @NotBlank
        @Size(max = 500)
        @Schema(
            description = "Por qué se da de baja: demolido, sustituido, registro duplicado…",
            example = "Demolido por la crecida de 2026")
        String motivo) {}
