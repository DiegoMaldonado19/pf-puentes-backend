package com.cunoc.puentes.formulario.dto;

import com.cunoc.puentes.common.ErrorCampo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Resultado de validar campos adicionales sin guardar nada")
public record ResultadoValidacionFormularioDTO(
    @Schema(description = "true si la composición puede guardarse y publicarse") boolean valido,
    @Schema(description = "Errores por campo; vacío si la composición es válida")
        List<ErrorCampo> errores) {}
