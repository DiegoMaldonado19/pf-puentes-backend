package com.cunoc.puentes.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El token del enlace que recibió el estudiante por correo. */
public record VerificarCorreoDTO(@NotBlank @Size(max = 200) String token) {

  // DT-SEC-12: el toString por defecto imprimiría el token
  @Override
  public String toString() {
    return "VerificarCorreoDTO[token=***]";
  }
}
