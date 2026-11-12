package com.cunoc.puentes.formulario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sección del formulario base")
public record SeccionFormularioDTO(
    @Schema(example = "datos_generales") String clave,
    @Schema(example = "Sección 2: Datos Generales y Geometría") String titulo,
    @JsonProperty("admite_campos_extra") boolean admiteCamposExtra) {}
