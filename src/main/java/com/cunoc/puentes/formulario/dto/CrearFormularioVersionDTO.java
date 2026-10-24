package com.cunoc.puentes.formulario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Datos para registrar un nuevo borrador de formulario")
public record CrearFormularioVersionDTO(
    @NotBlank
        @Size(max = 20)
        @Schema(example = "V2.0.0", description = "Código único de la versión")
        String codigo,
    @NotNull @Schema(description = "Estructura JSON Schema canónica")
        Map<String, Object> esquema) {}
