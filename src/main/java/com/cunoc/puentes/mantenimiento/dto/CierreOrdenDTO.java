package com.cunoc.puentes.mantenimiento.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** El conteo de evidencia se obtiene del servidor, nunca del cliente. */
public record CierreOrdenDTO(
    @NotNull @JsonProperty("fecha_ejecucion") LocalDate fechaEjecucion,
    @NotBlank String responsable) {}
