package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inserta la invitación y pide enviar el enlace cuando la transacción se confirme. */
@Service
class EmisorDeInvitacion {

  private final InvitacionRepository repositorio;
  private final ApplicationEventPublisher publicador;
  private final Duration vigencia;

  EmisorDeInvitacion(
      InvitacionRepository repositorio,
      ApplicationEventPublisher publicador,
      @Value("${sgp.invitacion.vigencia:P7D}") Duration vigencia) {
    this.repositorio = repositorio;
    this.publicador = publicador;
    this.vigencia = vigencia;
  }

  @Transactional
  @Auditable(accion = Acciones.INVITAR, entidad = "invitacion", entidadId = "#result.id")
  public Invitacion emitir(String correo, Rol rol, Usuario administrador) {
    Instant ahora = Instant.now();
    String token = TokenOpaco.generar();
    Invitacion invitacion = new Invitacion();
    invitacion.setCorreo(correo);
    invitacion.setRol(rol);
    invitacion.setTokenHash(TokenOpaco.hash(token));
    invitacion.setExpiraEn(ahora.plus(vigencia));
    invitacion.setAdministrador(administrador);
    Invitacion guardada = repositorio.saveAndFlush(invitacion);
    publicador.publishEvent(new InvitacionEmitida(guardada.getId(), correo, rol, token, vigencia));
    return guardada;
  }
}
