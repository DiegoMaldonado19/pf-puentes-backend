package com.cunoc.puentes.usuario.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.usuario.Rol;
import org.junit.jupiter.api.Test;

/** DT-SEC-12: ni contraseñas ni tokens llegan a un log por el toString de un DTO. */
class DatosSensiblesDeUsuarioTest {

  private static final String CONTRASENA = "Secreta12345";
  private static final String TOKEN = "token-de-prueba-123";

  @Test
  void losDtoConSecretosNoLosImprimen() {
    assertThat(new AceptarInvitacionDTO(TOKEN, "Ana", CONTRASENA, null).toString())
        .doesNotContain(TOKEN, CONTRASENA);
    assertThat(new CambiarContrasenaDTO(CONTRASENA, CONTRASENA + "x").toString())
        .doesNotContain(CONTRASENA);
    assertThat(new RestablecerContrasenaDTO(TOKEN, CONTRASENA).toString())
        .doesNotContain(TOKEN, CONTRASENA);
    assertThat(new TokenInvitacionDTO(TOKEN).toString()).doesNotContain(TOKEN);
  }

  @Test
  void normalizaCorreosNombresYMotivos() {
    assertThat(new CrearInvitacionDTO("  Prof@Correo.COM ", Rol.CATEDRATICO).correo())
        .isEqualTo("prof@correo.com");
    assertThat(new RecuperarContrasenaDTO(" Ana@Correo.com ").correo()).isEqualTo("ana@correo.com");
    assertThat(new MotivoDTO("  Ya no pertenece  ").motivo()).isEqualTo("Ya no pertenece");
    AceptarInvitacionDTO aceptar = new AceptarInvitacionDTO(TOKEN, "  Ana  ", CONTRASENA, "  ");
    assertThat(aceptar.nombre()).isEqualTo("Ana");
    assertThat(aceptar.numeroColegiado()).isNull();
    assertThat(new AceptarInvitacionDTO(TOKEN, "Ana", CONTRASENA, " 1234 ").numeroColegiado())
        .isEqualTo("1234");
  }
}
