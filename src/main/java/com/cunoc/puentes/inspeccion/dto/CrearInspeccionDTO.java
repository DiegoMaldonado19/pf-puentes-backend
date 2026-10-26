package com.cunoc.puentes.inspeccion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CrearInspeccionDTO(
    @NotNull @UuidV7 UUID id,
    @NotNull @JsonProperty("puente_id") UUID puenteId,
    @JsonProperty("asignacion_id") UUID asignacionId,
    @JsonProperty("curso_id") UUID cursoId,
    @NotNull @JsonProperty("fecha_inspeccion") LocalDate fechaInspeccion,
    @NotBlank @Size(max = 100) @JsonProperty("dispositivo_id") String dispositivoId,
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double latitud,
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double longitud,
    @JsonProperty("creada_en_dispositivo") Instant creadaEnDispositivo,
    @JsonProperty("supersede_a_id") UUID supersedeAId,
    @Size(max = 500) @JsonProperty("motivo_correccion") String motivoCorreccion) {}
