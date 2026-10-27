package com.cunoc.puentes.revision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Nunca contiene calificación: se consulta por un endpoint privado independiente. */
public record HistorialRevisionDTO(
    UUID id,
    @JsonProperty("revisor_id") UUID revisorId,
    Veredicto veredicto,
    String observacion,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("emitida_en") Instant emitidaEn,
    String estado,
    List<ObservacionRevisionDTO> observaciones) {}
