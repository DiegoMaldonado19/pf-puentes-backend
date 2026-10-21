package com.cunoc.puentes.puente.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Coordenada UTM WGS84 calculada al mostrar (RN-INV-05)")
public record UtmDTO(
    @Schema(description = "Zona UTM: 15 o 16 en Guatemala", example = "15") int zona,
    @Schema(description = "Hemisferio", example = "N") String hemisferio,
    @Schema(description = "Este en metros", example = "659463.36") double este,
    @Schema(description = "Norte en metros", example = "1640571.32") double norte) {}
