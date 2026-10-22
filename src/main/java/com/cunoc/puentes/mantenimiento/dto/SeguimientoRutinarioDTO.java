package com.cunoc.puentes.mantenimiento.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

/** Resumen global y página de órdenes abiertas: los conteos no dependen de la página solicitada. */
public record SeguimientoRutinarioDTO(
    @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("fecha_evaluacion") LocalDate fechaEvaluacion,
    @JsonProperty("fecha_referencia") LocalDate fechaReferencia,
    @JsonProperty("origen_referencia") String origenReferencia,
    @JsonProperty("fecha_limite") LocalDate fechaLimite,
    @JsonProperty("programadas_en_plazo") long programadasEnPlazo,
    @JsonProperty("sin_fecha") long sinFecha,
    @JsonProperty("fechas_pasadas") long fechasPasadas,
    @JsonProperty("posteriores_al_limite") long posterioresAlLimite,
    PaginaOrdenesDTO ordenes) {}
