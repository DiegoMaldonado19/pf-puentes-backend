package com.cunoc.puentes.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Justificación escrita que exigen la baja, la reactivación y el rechazo del colegiado. */
public record MotivoDTO(@NotBlank @Size(max = 500) String motivo) {

  public MotivoDTO {
    motivo = motivo == null ? null : motivo.strip();
  }
}
