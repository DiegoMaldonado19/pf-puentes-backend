package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cierra la invitación al crearse la cuenta; la auditoría guarda el antes y el después. */
@Service
class AceptadorDeInvitacion {

  private final InvitacionRepository repositorio;

  AceptadorDeInvitacion(InvitacionRepository repositorio) {
    this.repositorio = repositorio;
  }

  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "invitacion", entidadId = "#invitacionId")
  public void aceptar(UUID invitacionId, Usuario cuenta) {
    Invitacion invitacion = repositorio.findById(invitacionId).orElseThrow();
    invitacion.setAceptadaEn(Instant.now());
    invitacion.setUsuario(cuenta);
  }
}
