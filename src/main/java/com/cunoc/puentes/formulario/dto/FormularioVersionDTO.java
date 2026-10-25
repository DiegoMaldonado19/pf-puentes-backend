package com.cunoc.puentes.formulario.dto;

import com.cunoc.puentes.formulario.EstadoFormulario;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Versión del formulario de evaluación de puentes")
public record FormularioVersionDTO(
    UUID id,
    @Schema(example = "V1.0.0") String codigo,
    @Schema(description = "Estructura JSON Schema con metadatos del motor")
        Map<String, Object> esquema,
    @Schema(description = "Mapeo de campos respecto a la versión anterior")
        Map<String, Object> mapeo,
    EstadoFormulario estado,
    @JsonProperty("publicada_en") Instant publicadaEn,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("actualizado_en") Instant actualizadoEn) {}
