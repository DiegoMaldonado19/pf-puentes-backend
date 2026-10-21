package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** US-P2-11: cambio de contraseña con sesión; la anterior deja de servir. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class CambioContrasenaTest extends PruebaIntegracion {

  private static final String DOMINIO = "@cambio-contrasena-prueba.com";
  private static final String CORREO = "carlos" + DOMINIO;
  private static final String ACTUAL = "Puentes2026ok";
  private static final String NUEVA = "Puentes2027ok";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;

  private Usuario cuenta;

  @BeforeEach
  void preparar() {
    borrarDatosDePrueba();
    cuenta = guardar(Cuentas.estudiante(CORREO));
  }

  @AfterEach
  void limpiar() {
    borrarDatosDePrueba();
  }

  private void borrarDatosDePrueba() {
    String cuentas = "SELECT id FROM usuario WHERE correo LIKE '%" + DOMINIO + "'";
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad_id IN ("
            + cuentas
            + ") OR usuario_id IN ("
            + cuentas
            + ")");
    jdbc.update("DELETE FROM token_renovacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%" + DOMINIO + "'");
  }

  private Usuario guardar(Usuario usuario) {
    usuario.setNombre("Carlos Ruiz");
    usuario.setContrasenaHash(codificador.encode(ACTUAL));
    return usuarios.saveAndFlush(usuario);
  }

  private ResultActions cambiar(UUID id, String actual, String nueva) throws Exception {
    return mvc.perform(
        put("/api/v1/usuarios/me/contrasena")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"contrasena_actual\":\"%s\",\"contrasena_nueva\":\"%s\"}"
                    .formatted(actual, nueva))
            .with(como(Rol.ESTUDIANTE, id)));
  }

  private ResultActions login(String correo, String contrasena) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
  }

  @Test
  void elCambioRevocaLasSesionesYLaContrasenaViejaDejaDeServir(CapturedOutput salida)
      throws Exception {
    MvcResult inicio = login(CORREO, ACTUAL).andExpect(status().isOk()).andReturn();
    String cookie =
        inicio
            .getResponse()
            .getHeader(HttpHeaders.SET_COOKIE)
            .substring("refresh_token=".length())
            .split(";", 2)[0];

    cambiar(cuenta.getId(), ACTUAL, NUEVA).andExpect(status().isNoContent());

    login(CORREO, ACTUAL)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/credenciales-invalidas")));
    login(CORREO, NUEVA).andExpect(status().isOk());

    mvc.perform(post("/api/v1/auth/renovar").cookie(new Cookie("refresh_token", cookie)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/sesion-invalida")));

    String hash = usuarios.findById(cuenta.getId()).orElseThrow().getContrasenaHash();
    assertThat(hash).startsWith("$2a$12$").doesNotContain(ACTUAL).doesNotContain(NUEVA);
    assertThat(salida.getAll()).doesNotContain(ACTUAL, NUEVA, hash, cookie);
  }

  @Test
  void rechazaLaActualIncorrectaYLaNuevaIgualALaActual() throws Exception {
    cambiar(cuenta.getId(), "OtraClave999", NUEVA)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(endsWith("/contrasena-actual-incorrecta")));
    cambiar(cuenta.getId(), ACTUAL, ACTUAL)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(endsWith("/contrasena-repetida")));

    login(CORREO, ACTUAL).andExpect(status().isOk());
  }

  @Test
  void auditaElCambioSinHashNiContrasena() throws Exception {
    cambiar(cuenta.getId(), ACTUAL, NUEVA).andExpect(status().isNoContent());

    List<String> acciones =
        jdbc.queryForList(
            "SELECT accion FROM auditoria WHERE entidad_id = ? ORDER BY creado_en",
            String.class,
            cuenta.getId());
    assertThat(acciones).containsExactly("MODIFICAR");
    String valores =
        jdbc.queryForObject(
            "SELECT string_agg(coalesce(valor_anterior::text, '') || ' ' || coalesce(valor_nuevo::text, ''), ' ')"
                + " FROM auditoria WHERE entidad_id = ?",
            String.class,
            cuenta.getId());
    String hash = usuarios.findById(cuenta.getId()).orElseThrow().getContrasenaHash();
    assertThat(valores).doesNotContain(ACTUAL, NUEVA, hash, "$2a$");
  }
}
