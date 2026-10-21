package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marca una invitación abierta como revocada; la fila de auditoría la escribe el aspecto. */
@Service
class RevocadorDeInvitacion {

  private final InvitacionRepository repositorio;

  RevocadorDeInvitacion(InvitacionRepository repositorio) {
    this.repositorio = repositorio;
  }

  @Transactional
  @Auditable(
      accion = Acciones.REVOCAR_INVITACION,
      entidad = "invitacion",
      entidadId = "#invitacionId")
  public Invitacion revocar(UUID invitacionId, Usuario administrador) {
    Invitacion invitacion = repositorio.findById(invitacionId).orElseThrow();
    invitacion.setRevocadaEn(Instant.now());
    invitacion.setRevocadaPor(administrador);
    return invitacion;
  }
}
