package com.cunoc.puentes.usuario.dto;

import com.cunoc.puentes.usuario.EstadoUsuario;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;

/** Los datos propios del usuario autenticado. Nunca el hash ni datos de otras cuentas. */
public record PerfilDTO(UUID id, String nombre, String correo, Rol rol, EstadoUsuario estado) {}
