package com.cunoc.puentes.curso.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AsignarPuenteDTO(@NotNull @JsonProperty("puente_id") UUID puenteId) {}
