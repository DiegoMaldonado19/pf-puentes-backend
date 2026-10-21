package com.cunoc.puentes.puente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aprobación de una solicitud de alta")
public record AprobarSolicitudDTO(
    @JsonProperty("confirmar_duplicado")
        @Schema(description = "true si el Administrador ya vio la advertencia de duplicados")
        Boolean confirmarDuplicado) {

  public boolean confirmado() {
    return Boolean.TRUE.equals(confirmarDuplicado);
  }
}
