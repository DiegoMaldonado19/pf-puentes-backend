package com.cunoc.puentes.indice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record PesoElementoDTO(
    UUID id, @JsonProperty("elemento_ref") String elementoRef, BigDecimal peso) {}
