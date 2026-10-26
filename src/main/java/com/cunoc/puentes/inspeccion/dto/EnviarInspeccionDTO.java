package com.cunoc.puentes.inspeccion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Hora de envío del dispositivo (D11). Si falta, el servidor usa el instante de recepción. */
public record EnviarInspeccionDTO(@JsonProperty("enviada_en") Instant enviadaEn) {}
