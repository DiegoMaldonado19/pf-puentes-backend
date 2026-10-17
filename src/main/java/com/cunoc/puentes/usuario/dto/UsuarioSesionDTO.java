package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;

/** Quién inició sesión; lo que el frontend necesita para pintar la interfaz. */
public record UsuarioSesionDTO(UUID id, String nombre, Rol rol) {}
