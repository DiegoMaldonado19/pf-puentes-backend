package com.cunoc.puentes.inspeccion.dto;

import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record HistorialTransicionDTO(
    UUID id,
    @JsonProperty("estado_origen") EstadoInspeccion estadoOrigen,
    @JsonProperty("estado_destino") EstadoInspeccion estadoDestino,
    @JsonProperty("actor_id") UUID actorId,
    String motivo,
    @JsonProperty("creado_en") Instant creadoEn) {}
