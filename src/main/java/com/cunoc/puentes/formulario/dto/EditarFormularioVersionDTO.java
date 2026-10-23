package com.cunoc.puentes.formulario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Schema(description = "Datos para editar el esquema de un borrador existente")
public record EditarFormularioVersionDTO(
    @NotNull
        @Schema(description = "Estructura JSON Schema canónica actualizada")
        Map<String, Object> esquema) {}
