package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Puente activo a menos de 100 m de un punto: posible duplicado (RN-INV-06)")
public record PuenteCercanoDTO(
    UUID id,
    @Schema(example = "GT-09-0901-0001") String codigo,
    String nombre,
    @JsonProperty("distancia_metros") @Schema(description = "Distancia geodésica en metros")
        double distanciaMetros) {}
