package com.cunoc.puentes.foro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Resumen de un puente para acceder a su foro permanente")
public record PuenteForoDTO(
    UUID id,
    String codigo,
    String nombre,
    String ruta,
    String municipio,
    String departamento,
    @JsonProperty("estado_actual") String estadoActual,
    @JsonProperty("cantidad_hilos") long cantidadHilos) {}
