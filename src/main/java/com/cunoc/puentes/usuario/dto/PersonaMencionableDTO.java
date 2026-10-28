package com.cunoc.puentes.usuario.dto;

import java.util.UUID;

/** RN-FOR-10: solo lo necesario para elegir a quién mencionar; nunca el correo ni el rol. */
public record PersonaMencionableDTO(UUID id, String nombre) {}
