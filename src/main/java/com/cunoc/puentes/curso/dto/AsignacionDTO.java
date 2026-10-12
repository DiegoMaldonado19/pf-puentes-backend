package com.cunoc.puentes.curso.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record AsignacionDTO(
    UUID id,
    @JsonProperty("inscripcion_id") UUID inscripcionId,
    @JsonProperty("curso_id") UUID cursoId,
    @JsonProperty("estudiante_id") UUID estudianteId,
    @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("curso_nombre") String cursoNombre,
    @JsonProperty("puente_codigo") String puenteCodigo,
    @JsonProperty("puente_nombre") String puenteNombre,
    @JsonProperty("creado_en") Instant creadoEn) {}
