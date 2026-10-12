package com.cunoc.puentes.curso.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record InscripcionDTO(
    UUID id,
    @JsonProperty("curso_id") UUID cursoId,
    @JsonProperty("estudiante_id") UUID estudianteId,
    @JsonProperty("creado_en") Instant creadoEn) {}
