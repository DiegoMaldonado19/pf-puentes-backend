package com.cunoc.puentes.inspeccion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record AcompananteDTO(String nombre, @JsonProperty("usuario_id") UUID usuarioId) {}
