package com.cunoc.puentes.usuario;

import java.time.Duration;
import java.util.UUID;

/**
 * Se publica al crear una invitación y se atiende después del commit. El token en claro solo vive
 * en este evento, en memoria; por eso {@code toString} no lo muestra (DT-SEC-12).
 */
record InvitacionEmitida(
    UUID invitacionId, String correo, Rol rol, String token, Duration vigencia) {

  @Override
  public String toString() {
    return "InvitacionEmitida[invitacionId=" + invitacionId + "]";
  }
}
