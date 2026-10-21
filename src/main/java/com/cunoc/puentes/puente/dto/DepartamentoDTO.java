package com.cunoc.puentes.puente.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Departamento del catálogo INE")
public record DepartamentoDTO(
    @Schema(description = "Código INE de dos dígitos", example = "09") String codigo,
    @Schema(description = "Nombre oficial", example = "Quetzaltenango") String nombre) {}
