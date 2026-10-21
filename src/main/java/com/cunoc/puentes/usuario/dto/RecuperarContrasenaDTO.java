package com.cunoc.puentes.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** DT-SEC-07: la respuesta es la misma exista o no la cuenta. */
public record RecuperarContrasenaDTO(@NotBlank @Email @Size(max = 254) String correo) {

  public RecuperarContrasenaDTO {
    correo = correo == null ? null : correo.strip().toLowerCase(Locale.ROOT);
  }
}
