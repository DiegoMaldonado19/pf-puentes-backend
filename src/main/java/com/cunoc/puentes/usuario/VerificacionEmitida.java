package com.cunoc.puentes.usuario;

import java.time.Duration;
import java.util.UUID;

/**
 * Se publica al emitir un token de verificación y se atiende después del commit. El token en claro
 * solo vive en este evento, en memoria; por eso {@code toString} no lo muestra (DT-SEC-12).
 */
record VerificacionEmitida(
    UUID usuarioId, String correo, String nombre, String token, Duration vigencia) {

  @Override
  public String toString() {
    return "VerificacionEmitida[usuarioId=" + usuarioId + "]";
  }
}
