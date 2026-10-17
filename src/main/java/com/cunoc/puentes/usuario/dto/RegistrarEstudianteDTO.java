package com.cunoc.puentes.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Datos del registro autónomo (RN-USR-02). No hay campo de rol: cualquier "rol" que llegue en el
 * cuerpo se ignora y la cuenta nace como Estudiante.
 */
public record RegistrarEstudianteDTO(
    @NotBlank @Size(max = 150) String nombre,
    @NotBlank
        @Email
        @Size(max = 254)
        @Schema(description = "Se guarda en minúsculas y sin espacios")
        String correo,
    @NotNull
        @ContrasenaValida
        @Schema(
            description = "Mínimo 10 caracteres, con al menos una letra y un dígito (RN-USR-06)")
        String contrasena) {

  public RegistrarEstudianteDTO {
    nombre = nombre == null ? null : nombre.strip();
    correo = correo == null ? null : correo.strip().toLowerCase(Locale.ROOT);
  }

  // DT-SEC-12: el toString por defecto imprimiría la contraseña
  @Override
  public String toString() {
    return "RegistrarEstudianteDTO[contrasena=***]";
  }
}
