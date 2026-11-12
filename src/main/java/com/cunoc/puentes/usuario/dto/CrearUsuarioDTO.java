package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de una cuenta por el Administrador; el enlace para fijar la contraseña llega por correo. */
@Schema(description = "Alta de cuenta por un administrador")
public record CrearUsuarioDTO(
    @NotBlank @Size(max = 120) @Schema(example = "Ana Pérez") String nombre,
    @NotBlank @Email @Size(max = 254) @Schema(example = "ana@usac.edu.gt") String correo,
    @NotNull Rol rol) {}
