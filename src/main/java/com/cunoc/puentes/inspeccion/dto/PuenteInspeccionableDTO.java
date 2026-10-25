package com.cunoc.puentes.inspeccion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record PuenteInspeccionableDTO(
    UUID id,
    String codigo,
    String nombre,
    @JsonProperty("curso_id") UUID cursoId,
    @JsonProperty("curso_nombre") String cursoNombre,
    @JsonProperty("borrador_en_curso") boolean borradorEnCurso) {}
