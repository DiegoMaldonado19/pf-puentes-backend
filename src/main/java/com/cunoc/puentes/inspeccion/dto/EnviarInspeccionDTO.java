package com.cunoc.puentes.inspeccion.dto;

import com.cunoc.puentes.indice.EstadoCondicion;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hora de envío del dispositivo (D11). Si falta, el servidor usa el instante de recepción. El IC y
 * el estado calculado que mande el cliente se ignoran (US-P5-08).
 */
public record EnviarInspeccionDTO(
    @JsonProperty("enviada_en") Instant enviadaEn,
    @JsonProperty("indice_condicion") BigDecimal indiceCondicion,
    @JsonProperty("estado_calculado") EstadoCondicion estadoCalculado) {}
