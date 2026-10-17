package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.EstadoUsuario;
import com.cunoc.puentes.usuario.Rol;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** La cuenta recién creada. Nunca incluye la contraseña ni su hash. */
public record CuentaRegistradaDTO(
    UUID id,
    String nombre,
    String correo,
    Rol rol,
    EstadoUsuario estado,
    @JsonProperty("correo_verificado") boolean correoVerificado) {}
