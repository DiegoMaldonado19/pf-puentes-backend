package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** US-P2-04: el arranque crea el primer administrador una sola vez y sin filtrar secretos. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class AdministradorInicialTest extends PruebaIntegracion {

  private static final String CORREO = "admin-inicial@correo.com";
  private static final String NOMBRE = "Administrador Inicial";
  private static final String CONTRASENA = "clave-segura-2026";

  @Autowired UsuarioRepository usuarios;
  @Autowired CreadorDeAdministrador creador;
  @Autowired PasswordEncoder codificador;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void limpiar() {
    // Las pruebas de otras clases comparten la base: no deben ver administradores de esta
    jdbc.update("DELETE FROM auditoria WHERE entidad = 'usuario' AND usuario_id IS NULL");
    jdbc.update("DELETE FROM usuario WHERE rol = 'ADMINISTRADOR'");
  }

  private AdministradorInicial arranque(String correo, String nombre, String contrasena) {
    return new AdministradorInicial(usuarios, creador, correo, nombre, contrasena);
  }

  private void arrancar(AdministradorInicial arranque) {
    arranque.run(new DefaultApplicationArguments());
  }

  private List<Usuario> administradores() {
    return usuarios.findAll().stream().filter(u -> u.getRol() == Rol.ADMINISTRADOR).toList();
  }

  @Test
  void creaElAdministradorActivoConCorreoVerificadoYHashBcrypt12() {
    Instant antes = Instant.now();

    arrancar(arranque(CORREO, NOMBRE, CONTRASENA));

    Usuario admin = usuarios.buscarPorCorreo(CORREO).orElseThrow();
    assertThat(admin.getRol()).isEqualTo(Rol.ADMINISTRADOR);
    assertThat(admin.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
    assertThat(admin.getNombre()).isEqualTo(NOMBRE);
    assertThat(admin.getCorreoVerificadoEn()).isAfterOrEqualTo(antes);
    assertThat(admin.getContrasenaHash()).startsWith("$2a$12$");
    assertThat(codificador.matches(CONTRASENA, admin.getContrasenaHash())).isTrue();
  }

  @Test
  void esIdempotenteYNoCambiaLaContrasenaExistente() {
    arrancar(arranque(CORREO, NOMBRE, CONTRASENA));
    String hash = usuarios.buscarPorCorreo(CORREO).orElseThrow().getContrasenaHash();

    arrancar(arranque(CORREO, NOMBRE, "otra-clave-2027"));
    arrancar(arranque("otro@correo.com", NOMBRE, "otra-clave-2027"));

    assertThat(administradores()).hasSize(1);
    assertThat(usuarios.buscarPorCorreo(CORREO).orElseThrow().getContrasenaHash()).isEqualTo(hash);
    assertThat(auditoriasDeCreacion()).hasSize(1);
  }

  @Test
  void noCreaNadaSiYaHayOtroAdministradorAunqueTengaOtroCorreo() {
    usuarios.saveAndFlush(Cuentas.administrador("existente@correo.com"));

    arrancar(arranque(CORREO, NOMBRE, CONTRASENA));

    assertThat(usuarios.existePorCorreo(CORREO)).isFalse();
    assertThat(administradores()).hasSize(1);
  }

  @Test
  void sinVariablesAvisaEnElLogYNoFalla(CapturedOutput salida) {
    arrancar(arranque("", "", ""));

    assertThat(administradores()).isEmpty();
    assertThat(salida.getOut())
        .contains("WARN")
        .contains("ADMIN_INICIAL_CORREO")
        .contains("ADMIN_INICIAL_NOMBRE")
        .contains("ADMIN_INICIAL_CONTRASENA");
  }

  @Test
  void conVariablesIncompletasAvisaCualesFaltanYNoCrea(CapturedOutput salida) {
    arrancar(arranque(CORREO, NOMBRE, "  "));

    assertThat(administradores()).isEmpty();
    assertThat(salida.getOut()).contains("[ADMIN_INICIAL_CONTRASENA]");
  }

  @Test
  void rechazaUnaContrasenaDebilSinMostrarlaNiCrearLaCuenta() {
    for (String debil : List.of("corta1", "sin-ningun-digito", "1234567890123")) {
      assertThatThrownBy(() -> arrancar(arranque(CORREO, NOMBRE, debil)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ADMIN_INICIAL_CONTRASENA")
          .hasMessageContaining("10 caracteres")
          .hasMessageContaining("letra")
          .hasMessageContaining("dígito")
          .hasMessageNotContaining(debil);
    }
    assertThat(administradores()).isEmpty();
  }

  @Test
  void nuncaImprimeLaContrasenaNiSuHashEnLogsNiAuditoria(CapturedOutput salida) {
    arrancar(arranque(CORREO, NOMBRE, CONTRASENA));
    String hash = usuarios.buscarPorCorreo(CORREO).orElseThrow().getContrasenaHash();

    assertThat(salida.getAll()).doesNotContain(CONTRASENA).doesNotContain(hash);
    String valorNuevo = auditoriasDeCreacion().getFirst().get("valor_nuevo").toString();
    assertThat(valorNuevo).doesNotContain(CONTRASENA).doesNotContain(hash);
  }

  @Test
  void auditaLaCreacionComoAccionDelSistema() {
    arrancar(arranque(CORREO, NOMBRE, CONTRASENA));

    Map<String, Object> fila = auditoriasDeCreacion().getFirst();
    Usuario admin = usuarios.buscarPorCorreo(CORREO).orElseThrow();
    assertThat(fila.get("usuario_id")).isNull();
    assertThat(fila.get("entidad_id")).isEqualTo(admin.getId());
    assertThat(fila.get("valor_anterior")).isNull();
  }

  private List<Map<String, Object>> auditoriasDeCreacion() {
    return jdbc.queryForList(
        "SELECT usuario_id, entidad_id, valor_anterior::text AS valor_anterior,"
            + " valor_nuevo::text AS valor_nuevo FROM auditoria"
            + " WHERE accion = 'CREAR' AND entidad = 'usuario' AND usuario_id IS NULL"
            + " AND valor_nuevo ->> 'rol' = 'ADMINISTRADOR'");
  }
}
