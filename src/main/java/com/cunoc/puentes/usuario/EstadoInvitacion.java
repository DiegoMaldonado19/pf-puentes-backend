package com.cunoc.puentes.usuario;

import java.time.Instant;

/**
 * RN-USR-03: el estado no se guarda, se deriva de las marcas de la invitación. Aceptada y revocada
 * son definitivas; vencida solo aplica a una invitación todavía abierta.
 */
public enum EstadoInvitacion {
  ABIERTA,
  ACEPTADA,
  REVOCADA,
  VENCIDA;

  public static EstadoInvitacion de(Invitacion invitacion, Instant ahora) {
    if (invitacion.getAceptadaEn() != null) {
      return ACEPTADA;
    }
    if (invitacion.getRevocadaEn() != null) {
      return REVOCADA;
    }
    return invitacion.getExpiraEn().isAfter(ahora) ? ABIERTA : VENCIDA;
  }
}
