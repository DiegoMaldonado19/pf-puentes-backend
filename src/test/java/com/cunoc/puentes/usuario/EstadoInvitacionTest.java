package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.usuario.Cuentas.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstadoInvitacionTest {

  private final Usuario admin = Cuentas.administrador("admin@correo.com");

  @Test
  void unaInvitacionSinMarcasYConPlazoVigenteEstaAbierta() {
    Invitacion invitacion = Cuentas.invitacion("prof@correo.com", admin);

    assertThat(EstadoInvitacion.de(invitacion, AHORA)).isEqualTo(EstadoInvitacion.ABIERTA);
  }

  @Test
  void venceAlLlegarExactamenteALaFechaDeExpiracion() {
    Invitacion invitacion = Cuentas.invitacion("prof@correo.com", admin);

    assertThat(EstadoInvitacion.de(invitacion, invitacion.getExpiraEn()))
        .isEqualTo(EstadoInvitacion.VENCIDA);
  }

  @Test
  void aceptadaYRevocadaSonDefinitivasAunqueHayaVencido() {
    Invitacion aceptada = Cuentas.invitacion("acepto@correo.com", admin);
    aceptada.setAceptadaEn(AHORA);
    Invitacion revocada = Cuentas.invitacion("revocada@correo.com", admin);
    revocada.setRevocadaEn(AHORA);
    var despuesDeVencer = AHORA.plusSeconds(7200);

    assertThat(EstadoInvitacion.de(aceptada, despuesDeVencer)).isEqualTo(EstadoInvitacion.ACEPTADA);
    assertThat(EstadoInvitacion.de(revocada, despuesDeVencer)).isEqualTo(EstadoInvitacion.REVOCADA);
  }
}
