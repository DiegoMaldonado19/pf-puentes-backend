package com.cunoc.puentes.usuario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** US-P2-11: cambio con sesión iniciada; la contraseña actual confirma que es el titular. */
public record CambiarContrasenaDTO(
    @JsonProperty("contrasena_actual") @NotBlank @Size(max = 128) String contrasenaActual,
    @JsonProperty("contrasena_nueva")
        @NotNull
        @ContrasenaValida
        @Schema(
            description = "Mínimo 10 caracteres, con al menos una letra y un dígito (RN-USR-06)")
        String contrasenaNueva) {

  // DT-SEC-12: el toString por defecto imprimiría las contraseñas
  @Override
  public String toString() {
    return "CambiarContrasenaDTO[contrasena_actual=***, contrasena_nueva=***]";
  }
}
