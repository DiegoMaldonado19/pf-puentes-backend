package com.cunoc.puentes.usuario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RN-USR-03, RN-USR-04: crea la cuenta invitada. El correo y el rol salen de la invitación, no del
 * cuerpo.
 */
public record AceptarInvitacionDTO(
    @NotBlank @Size(max = 200) String token,
    @NotBlank @Size(max = 150) String nombre,
    @NotNull
        @ContrasenaValida
        @Schema(
            description = "Mínimo 10 caracteres, con al menos una letra y un dígito (RN-USR-06)")
        String contrasena,
    @JsonProperty("numero_colegiado")
        @Size(max = 20)
        @Schema(description = "Obligatorio si la invitación es para PROFESIONAL_EXTERNO")
        String numeroColegiado) {

  public AceptarInvitacionDTO {
    nombre = nombre == null ? null : nombre.strip();
    numeroColegiado =
        numeroColegiado == null || numeroColegiado.isBlank() ? null : numeroColegiado.strip();
  }

  // DT-SEC-12: el toString por defecto imprimiría el token y la contraseña
  @Override
  public String toString() {
    return "AceptarInvitacionDTO[token=***, contrasena=***]";
  }
}
