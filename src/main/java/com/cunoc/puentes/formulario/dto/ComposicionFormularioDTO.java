package com.cunoc.puentes.formulario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Campos adicionales que componen una versión sobre el esquema base")
public record ComposicionFormularioDTO(
    @JsonProperty("campos_extra")
        @Schema(description = "Campos adicionales; puede ir vacío para usar solo la base")
        List<CampoExtraDTO> camposExtra) {}
