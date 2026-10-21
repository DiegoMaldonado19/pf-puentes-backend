package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.Rol;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Lo mínimo que ve quien abre el enlace antes de aceptar: sin ids ni datos del Administrador. */
public record InvitacionPublicaDTO(
    String correo, Rol rol, @JsonProperty("expira_en") Instant expiraEn) {}
