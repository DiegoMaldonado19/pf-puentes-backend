package com.cunoc.puentes.foro.dto;

import com.cunoc.puentes.foro.CategoriaHilo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear un hilo en el foro")
public record CrearHiloDTO(
    @NotBlank(message = "El título no puede estar vacío.")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres.")
        @Schema(description = "Título del hilo", example = "Fisura en el estribo de entrada")
        String titulo,
    @NotNull(message = "La categoría es obligatoria.")
        @Schema(description = "Categoría del hilo", example = "discusion_tecnica")
        CategoriaHilo categoria) {}
