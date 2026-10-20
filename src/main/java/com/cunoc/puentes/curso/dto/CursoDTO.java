package com.cunoc.puentes.curso.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record CursoDTO(
    UUID id,
    String nombre,
    int anio,
    int semestre,
    @JsonProperty("catedratico_id") UUID catedraticoId,
    boolean vigente,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("actualizado_en") Instant actualizadoEn) {}
