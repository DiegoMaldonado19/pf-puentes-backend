package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.usuario.Cuentas.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.usuario.dto.InvitacionDTO;
import com.cunoc.puentes.usuario.dto.UsuarioAdminDTO;
import org.junit.jupiter.api.Test;

class MapeadoresDeUsuarioTest {

  private final UsuarioMapper usuarios = new UsuarioMapperImpl();
  private final InvitacionMapper invitaciones = new InvitacionMapperImpl();

  @Test
  void laVistaDelAdministradorTraeLaBajaYElColegiadoPeroNoElHash() {
    Usuario profesional = Cuentas.estudiante("profesional@correo.com");
    profesional.setRol(Rol.PROFESIONAL_EXTERNO);
    profesional.setNumeroColegiado("1234");
    profesional.setColegiadoVerificadoEn(AHORA);
    profesional.setEstado(EstadoUsuario.INACTIVO);
    profesional.setDesactivadoEn(AHORA);
    profesional.setMotivoBaja("Baja de prueba");

    UsuarioAdminDTO dto = usuarios.aAdmin(profesional);

    assertThat(dto.correoVerificado()).isTrue();
    assertThat(dto.numeroColegiado()).isEqualTo("1234");
    assertThat(dto.colegiadoVerificadoEn()).isEqualTo(AHORA);
    assertThat(dto.desactivadoEn()).isEqualTo(AHORA);
    assertThat(dto.motivoBaja()).isEqualTo("Baja de prueba");
    assertThat(dto.toString()).doesNotContain("hash");
  }

  @Test
  void unaCuentaSinCorreoVerificadoSeMarcaComoTal() {
    Usuario pendiente = Cuentas.estudiante("pendiente@correo.com");
    pendiente.setCorreoVerificadoEn(null);

    assertThat(usuarios.aAdmin(pendiente).correoVerificado()).isFalse();
  }

  @Test
  void laInvitacionLlevaElEstadoDerivadoYAlAdministradorSinElToken() {
    Usuario admin = Cuentas.administrador("admin@correo.com");
    admin.setNombre("Admin Principal");
    Invitacion invitacion = Cuentas.invitacion("prof@correo.com", admin);

    InvitacionDTO dto = invitaciones.aDTO(invitacion, EstadoInvitacion.VENCIDA);

    assertThat(dto.estado()).isEqualTo(EstadoInvitacion.VENCIDA);
    assertThat(dto.administradorNombre()).isEqualTo("Admin Principal");
    assertThat(dto.usuarioId()).isNull();
    assertThat(dto.toString()).doesNotContain(invitacion.getTokenHash());
  }

  @Test
  void laVistaPublicaSoloTraeCorreoRolYExpiracion() {
    Invitacion invitacion =
        Cuentas.invitacion("prof@correo.com", Cuentas.administrador("admin@correo.com"));

    var publica = invitaciones.aPublica(invitacion);

    assertThat(publica.correo()).isEqualTo("prof@correo.com");
    assertThat(publica.rol()).isEqualTo(Rol.CATEDRATICO);
    assertThat(publica.expiraEn()).isEqualTo(invitacion.getExpiraEn());
  }
}
