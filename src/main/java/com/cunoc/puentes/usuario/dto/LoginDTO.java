package com.cunoc.puentes.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Credenciales. No se valida el formato del correo: un valor raro es solo "credenciales inválidas".
 */
public record LoginDTO(
    @NotBlank @Size(max = 254) String correo, @NotBlank @Size(max = 128) String contrasena) {

  public LoginDTO {
    correo = correo == null ? null : correo.strip().toLowerCase(Locale.ROOT);
  }

  // DT-SEC-12: el toString por defecto imprimiría la contraseña
  @Override
  public String toString() {
    return "LoginDTO[contrasena=***]";
  }
}
