package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.EstadoUsuario;
import com.cunoc.puentes.usuario.Rol;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Una cuenta vista por el Administrador (RN-USR-08). Nunca el hash ni los contadores de bloqueo.
 */
public record UsuarioAdminDTO(
    UUID id,
    String nombre,
    String correo,
    Rol rol,
    EstadoUsuario estado,
    @JsonProperty("correo_verificado") boolean correoVerificado,
    @JsonProperty("numero_colegiado") String numeroColegiado,
    @JsonProperty("colegiado_verificado_en") Instant colegiadoVerificadoEn,
    @JsonProperty("desactivado_en") Instant desactivadoEn,
    @JsonProperty("motivo_baja") String motivoBaja,
    @JsonProperty("creado_en") Instant creadoEn) {}
