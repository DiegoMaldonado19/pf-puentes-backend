package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.EstadoInvitacion;
import com.cunoc.puentes.usuario.Rol;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Una invitación vista por el Administrador. Nunca incluye el token ni su hash. */
public record InvitacionDTO(
    UUID id,
    String correo,
    Rol rol,
    EstadoInvitacion estado,
    @JsonProperty("expira_en") Instant expiraEn,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("administrador_id") UUID administradorId,
    @JsonProperty("administrador_nombre") String administradorNombre,
    @JsonProperty("usuario_id") UUID usuarioId) {}
