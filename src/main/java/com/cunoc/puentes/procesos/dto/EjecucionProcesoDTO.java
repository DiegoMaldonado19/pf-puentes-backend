package com.cunoc.puentes.procesos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Resultado de una ejecución manual de proceso programado")
public record EjecucionProcesoDTO(
    @Schema(description = "Identificador estable del proceso") UUID id,
    @Schema(example = "alertas-inspeccion") String nombre,
    @JsonProperty("iniciado_en") Instant iniciadoEn,
    @JsonProperty("terminado_en") Instant terminadoEn) {}
