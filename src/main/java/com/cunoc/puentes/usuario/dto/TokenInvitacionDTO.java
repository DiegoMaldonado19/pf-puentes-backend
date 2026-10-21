package com.cunoc.puentes.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El token del enlace de invitación, para consultar a qué correo y rol corresponde. */
public record TokenInvitacionDTO(@NotBlank @Size(max = 200) String token) {

  // DT-SEC-12: el toString por defecto imprimiría el token
  @Override
  public String toString() {
    return "TokenInvitacionDTO[token=***]";
  }
}
