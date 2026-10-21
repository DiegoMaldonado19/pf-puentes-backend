package com.cunoc.puentes.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El token del enlace de recuperación y la contraseña nueva. */
public record RestablecerContrasenaDTO(
    @NotBlank @Size(max = 200) String token,
    @NotNull
        @ContrasenaValida
        @Schema(
            description = "Mínimo 10 caracteres, con al menos una letra y un dígito (RN-USR-06)")
        String contrasena) {

  // DT-SEC-12: el toString por defecto imprimiría el token y la contraseña
  @Override
  public String toString() {
    return "RestablecerContrasenaDTO[token=***, contrasena=***]";
  }
}
