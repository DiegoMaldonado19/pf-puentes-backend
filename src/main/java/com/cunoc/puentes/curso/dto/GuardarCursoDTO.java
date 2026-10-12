package com.cunoc.puentes.curso.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GuardarCursoDTO(
    @NotBlank @Size(max = 150) String nombre,
    @NotNull @Min(2020) @Max(2100) Integer anio,
    @NotNull @Min(1) @Max(2) Integer semestre,
    @NotNull Boolean vigente) {}
