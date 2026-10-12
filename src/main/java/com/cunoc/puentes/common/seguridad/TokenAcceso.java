package com.cunoc.puentes.common.seguridad;

import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;

/** Lo único que lleva un token de acceso: quién es y con qué rol se emitió (DT-SEC-01). */
record TokenAcceso(UUID usuarioId, Rol rol) {}
