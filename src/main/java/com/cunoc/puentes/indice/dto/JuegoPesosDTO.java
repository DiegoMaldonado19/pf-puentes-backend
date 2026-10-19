package com.cunoc.puentes.indice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** La presencia de una referencia de acta no certifica su firma ni la calibración. */
public record JuegoPesosDTO(
    UUID id,
    @JsonProperty("formulario_version_id") UUID formularioVersionId,
    String acta,
    @JsonProperty("vigente_desde") Instant vigenteDesde) {}
