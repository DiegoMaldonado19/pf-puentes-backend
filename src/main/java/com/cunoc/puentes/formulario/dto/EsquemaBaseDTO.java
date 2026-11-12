package com.cunoc.puentes.formulario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(
    description =
        "Esquema base SIECA definido en código y secciones que admiten campos adicionales")
public record EsquemaBaseDTO(
    @Schema(description = "JSON Schema base sin campos adicionales") Map<String, Object> esquema,
    List<SeccionFormularioDTO> secciones) {}
