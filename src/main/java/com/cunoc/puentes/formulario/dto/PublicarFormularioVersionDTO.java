package com.cunoc.puentes.formulario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Datos para publicar una versión de formulario y registrar el mapeo histórico")
public record PublicarFormularioVersionDTO(
    @Schema(description = "Mapeo de equivalencia de campos respecto a la versión anterior (RN-FRM-06)")
        Map<String, Object> mapeo) {}
