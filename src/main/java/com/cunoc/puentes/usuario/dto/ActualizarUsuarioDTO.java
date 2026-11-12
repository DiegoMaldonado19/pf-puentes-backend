package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edición de nombre y correo; el rol es opcional y solo cambia entre CATEDRATICO y ADMINISTRADOR.
 */
@Schema(description = "Edición de una cuenta por un administrador")
public record ActualizarUsuarioDTO(
    @NotBlank @Size(max = 120) String nombre,
    @NotBlank @Email @Size(max = 254) String correo,
    @Schema(description = "Opcional. Solo CATEDRATICO o ADMINISTRADOR") Rol rol) {}
