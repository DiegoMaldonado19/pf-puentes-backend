package com.cunoc.puentes.foro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Datos para editar el texto de un comentario existente")
public record EditarComentarioDTO(
    @NotBlank(message = "El texto no puede estar vacío.")
        @Schema(
            description = "Nuevo texto del comentario",
            example = "Texto corregido antes de los 15 minutos.")
        String texto) {}
