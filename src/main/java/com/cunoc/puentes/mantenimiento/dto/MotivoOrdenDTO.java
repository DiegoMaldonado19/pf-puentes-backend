package com.cunoc.puentes.mantenimiento.dto;

import jakarta.validation.constraints.NotBlank;

/** RN-MTO-09: se conserva el motivo escrito sin truncarlo. */
public record MotivoOrdenDTO(@NotBlank String motivo) {}
