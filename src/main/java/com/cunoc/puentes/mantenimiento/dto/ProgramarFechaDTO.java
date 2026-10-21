package com.cunoc.puentes.mantenimiento.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Fecha civil de la intervención, no un instante ni un estado elegido por el cliente. */
public record ProgramarFechaDTO(
    @NotNull
        @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "uuuu-MM-dd",
            lenient = OptBoolean.FALSE)
        @JsonProperty("fecha_programada")
        LocalDate fechaProgramada) {}
