package com.cunoc.puentes.inspeccion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AcompananteDTO(
    @NotBlank @Size(max = 150) String nombre, @JsonProperty("usuario_id") UUID usuarioId) {}
