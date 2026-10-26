package com.cunoc.puentes.inspeccion.dto;

import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InspeccionResumenDTO(
    UUID id,
    PuenteResumenDTO puente,
    EstadoInspeccion estado,
    @JsonProperty("fecha_inspeccion") LocalDate fechaInspeccion,
    @JsonProperty("curso_id") UUID cursoId,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("superada") boolean superada,
    @JsonProperty("superada_por") UUID superadaPor) {}
