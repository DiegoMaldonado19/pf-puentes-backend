package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Municipio del catálogo INE")
public record MunicipioDTO(
    @Schema(description = "Código INE de cuatro dígitos", example = "0901") String codigo,
    @Schema(description = "Nombre oficial", example = "Quetzaltenango") String nombre,
    @JsonProperty("departamento_codigo")
        @Schema(description = "Departamento al que pertenece", example = "09")
        String departamentoCodigo) {}
