package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** RN-USR-03: el Administrador invita a una cuenta con rol; Estudiante no se invita. */
public record CrearInvitacionDTO(
    @NotBlank
        @Email
        @Size(max = 254)
        @Schema(description = "Se guarda en minúsculas y sin espacios")
        String correo,
    @NotNull @Schema(description = "Cualquier rol excepto ESTUDIANTE") Rol rol) {

  public CrearInvitacionDTO {
    correo = correo == null ? null : correo.strip().toLowerCase(Locale.ROOT);
  }
}
