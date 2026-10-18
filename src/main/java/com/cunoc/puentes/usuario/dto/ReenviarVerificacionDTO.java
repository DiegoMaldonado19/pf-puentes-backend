package com.cunoc.puentes.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record ReenviarVerificacionDTO(@NotBlank @Email @Size(max = 254) String correo) {

  public ReenviarVerificacionDTO {
    correo = correo == null ? null : correo.strip().toLowerCase(Locale.ROOT);
  }
}
