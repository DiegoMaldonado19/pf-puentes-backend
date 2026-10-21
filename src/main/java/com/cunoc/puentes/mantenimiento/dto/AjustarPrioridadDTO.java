package com.cunoc.puentes.mantenimiento.dto;

import com.cunoc.puentes.mantenimiento.Prioridad;
import jakarta.validation.constraints.NotNull;

/** El dominio exige justificación cuando realmente cambia la prioridad vigente. */
public record AjustarPrioridadDTO(@NotNull Prioridad prioridad, String justificacion) {}
