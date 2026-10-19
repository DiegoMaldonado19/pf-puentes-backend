package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** US-P2-07: renovar, cerrar sesión, revocar todas y consultar el perfil propio. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RenovacionDeSesionTest extends PruebaIntegracion {

  private static final String DOMINIO = "@renovacion-prueba.com";
  private static final String CORREO = "carlos" + DOMINIO;
  private static final String CONTRASENA = "Puentes2026ok";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;
  @Autowired SesionService sesiones;

  private UUID carlos;

  @BeforeEach
  void crearCuenta() {
    limpiar();
    Usuario cuenta = Cuentas.estudiante(CORREO);
    cuenta.setNombre("Carlos Ruiz");
    cuenta.setContrasenaHash(codificador.encode(CONTRASENA));
    carlos = usuarios.save(cuenta).getId();
  }

  @AfterEach
  void limpiar() {
    String cuentas = "SELECT id FROM usuario WHERE correo LIKE '%" + DOMINIO + "'";
    jdbc.update("DELETE FROM auditoria WHERE entidad_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM token_renovacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%" + DOMINIO + "'");
  }

  /** Inicia sesión y devuelve la respuesta completa (cuerpo y cookie). */
  private MvcResult iniciarSesion() throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"correo": "%s", "contrasena": "%s"}
                    """
                        .formatted(CORREO, CONTRASENA)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private static String cookieDe(MvcResult login) {
    String cabecera = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    return cabecera.substring("refresh_token=".length(), cabecera.indexOf(';'));
  }

  private String nuevaSesion() throws Exception {
    return cookieDe(iniciarSesion());
  }

  private ResultActions renovar(String cookie) throws Exception {
    return mvc.perform(post("/api/v1/auth/renovar").cookie(new Cookie("refresh_token", cookie)));
  }

  private ResultActions cerrarSesion(String cookie) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/cerrar-sesion").cookie(new Cookie("refresh_token", cookie)));
  }

  private void exigirRechazoConCookieBorrada(ResultActions resultado) throws Exception {
    resultado
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/sesion-invalida")));
    assertThat(resultado.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .contains("refresh_token=;", "Max-Age=0", "HttpOnly");
  }

  private int tokensVigentes() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM token_renovacion WHERE usuario_id = ? AND revocado_en IS NULL",
        Integer.class,
        carlos);
  }

  @Test
  void renuevaElTokenDeAccesoSinDevolverElDeRenovacion() throws Exception {
    String cookie = nuevaSesion();

    MvcResult renovada =
        renovar(cookie)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(900))
            .andExpect(jsonPath("$.usuario.id").value(carlos.toString()))
            .andReturn();

    String cuerpo = renovada.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(cuerpo).doesNotContain(cookie).doesNotContain("refresh");
    assertThat(renovada.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    // El mismo token sigue sirviendo: no hay rotación (ADR 0017)
    renovar(cookie).andExpect(status().isOk());
  }

  @Test
  void elTokenDeAccesoRenovadoAbreRutasPrivadas() throws Exception {
    String acceso = JsonPath.read(contenido(renovarYObtener(nuevaSesion())), "$.access_token");

    mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + acceso))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(carlos.toString()));
  }

  private MvcResult renovarYObtener(String cookie) throws Exception {
    return renovar(cookie).andExpect(status().isOk()).andReturn();
  }

  private static String contenido(MvcResult resultado) throws Exception {
    return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void rechazaUnaCookieVencida() throws Exception {
    String cookie = nuevaSesion();
    jdbc.update(
        "UPDATE token_renovacion SET creado_en = now() - interval '8 days',"
            + " expira_en = now() - interval '1 day' WHERE usuario_id = ?",
        carlos);

    exigirRechazoConCookieBorrada(renovar(cookie));
  }

  @Test
  void rechazaUnaCookieRevocada() throws Exception {
    String cookie = nuevaSesion();
    jdbc.update("UPDATE token_renovacion SET revocado_en = now() WHERE usuario_id = ?", carlos);

    exigirRechazoConCookieBorrada(renovar(cookie));
  }

  @Test
  void rechazaUnaCookieQueNoExiste() throws Exception {
    exigirRechazoConCookieBorrada(renovar("cookie-que-nunca-se-emitio"));
  }

  @Test
  void rechazaLaCookieDeUnaCuentaQuePasoAInactivo() throws Exception {
    String cookie = nuevaSesion();
    jdbc.update(
        "UPDATE usuario SET estado = 'INACTIVO', desactivado_en = now(),"
            + " motivo_baja = 'Baja de prueba' WHERE id = ?",
        carlos);

    exigirRechazoConCookieBorrada(renovar(cookie));
  }

  @Test
  void sinCookieNoHayRenovacion() throws Exception {
    exigirRechazoConCookieBorrada(mvc.perform(post("/api/v1/auth/renovar")));
  }

  @Test
  void todosLosRechazosSonIndistinguibles() throws Exception {
    String revocada = nuevaSesion();
    jdbc.update("UPDATE token_renovacion SET revocado_en = now() WHERE usuario_id = ?", carlos);

    String conRevocada = contenido(renovar(revocada).andReturn());
    String inexistente = contenido(renovar("otra-cookie").andReturn());
    String sinCookie = contenido(mvc.perform(post("/api/v1/auth/renovar")).andReturn());

    assertThat(conRevocada).isEqualTo(inexistente).isEqualTo(sinCookie);
  }

  @Test
  void cerrarSesionRevocaElTokenBorraLaCookieYYaNoRenueva() throws Exception {
    String cookie = nuevaSesion();

    MvcResult cierre = cerrarSesion(cookie).andExpect(status().isNoContent()).andReturn();

    assertThat(cierre.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .contains("refresh_token=;", "Max-Age=0");
    assertThat(
            jdbc.queryForObject(
                "SELECT revocado_en FROM token_renovacion WHERE usuario_id = ?",
                Timestamp.class,
                carlos))
        .isNotNull();
    exigirRechazoConCookieBorrada(renovar(cookie));
  }

  @Test
  void cerrarSesionEsIdempotente() throws Exception {
    String cookie = nuevaSesion();
    cerrarSesion(cookie).andExpect(status().isNoContent());
    Timestamp primera =
        jdbc.queryForObject(
            "SELECT revocado_en FROM token_renovacion WHERE usuario_id = ?",
            Timestamp.class,
            carlos);

    cerrarSesion(cookie).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/cerrar-sesion")).andExpect(status().isNoContent());
    cerrarSesion("cookie-desconocida").andExpect(status().isNoContent());

    // La fecha de la primera revocación no se pisa
    assertThat(
            jdbc.queryForObject(
                "SELECT revocado_en FROM token_renovacion WHERE usuario_id = ?",
                Timestamp.class,
                carlos))
        .isEqualTo(primera);
  }

  @Test
  void cerrarUnaSesionNoCierraLasDemas() throws Exception {
    String telefono = nuevaSesion();
    String portatil = nuevaSesion();

    cerrarSesion(telefono).andExpect(status().isNoContent());

    renovar(portatil).andExpect(status().isOk());
    exigirRechazoConCookieBorrada(renovar(telefono));
  }

  @Test
  void revocarTodasLasSesionesDeUnUsuarioImpideRenovarEnCualquiera() throws Exception {
    String uno = nuevaSesion();
    String dos = nuevaSesion();
    String tres = nuevaSesion();
    assertThat(tokensVigentes()).isEqualTo(3);

    assertThat(sesiones.revocarTodas(carlos)).isEqualTo(3);

    assertThat(tokensVigentes()).isZero();
    for (String cookie : new String[] {uno, dos, tres}) {
      exigirRechazoConCookieBorrada(renovar(cookie));
    }
    assertThat(sesiones.revocarTodas(carlos)).isZero();
  }

  @Test
  void revocarTodasNoTocaLasSesionesDeOtrosUsuarios() throws Exception {
    String mia = nuevaSesion();
    Usuario otra = Cuentas.estudiante("otra" + DOMINIO);
    otra.setContrasenaHash(codificador.encode(CONTRASENA));
    UUID otraId = usuarios.save(otra).getId();
    jdbc.update(
        "INSERT INTO token_renovacion (id, usuario_id, token_hash, expira_en)"
            + " VALUES (gen_random_uuid(), ?, ?, now() + interval '1 day')",
        otraId,
        "a".repeat(64));

    sesiones.revocarTodas(carlos);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM token_renovacion WHERE usuario_id = ? AND revocado_en IS NULL",
                Integer.class,
                otraId))
        .isEqualTo(1);
    exigirRechazoConCookieBorrada(renovar(mia));
  }

  @Test
  void consultaElPerfilPropioSinDatosDeLosDemas() throws Exception {
    String acceso = JsonPath.read(contenido(iniciarSesion()), "$.access_token");
    Usuario otra = Cuentas.estudiante("otra" + DOMINIO);
    usuarios.save(otra);

    MvcResult perfil =
        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + acceso))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(carlos.toString()))
            .andExpect(jsonPath("$.nombre").value("Carlos Ruiz"))
            .andExpect(jsonPath("$.correo").value(CORREO))
            .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
            .andExpect(jsonPath("$.estado").value("ACTIVO"))
            .andReturn();

    assertThat(contenido(perfil))
        .doesNotContain("hash")
        .doesNotContain("$2a$")
        .doesNotContain("otra" + DOMINIO);
  }

  @Test
  void elPerfilPropioExigeSesion() throws Exception {
    mvc.perform(get("/api/v1/usuarios/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer token-falso"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void renovarNoGeneraFilasDeAuditoria() throws Exception {
    String cookie = nuevaSesion();
    int antes =
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria WHERE entidad_id = ? OR usuario_id = ?",
            Integer.class,
            carlos,
            carlos);

    for (int i = 0; i < 5; i++) {
      renovar(cookie).andExpect(status().isOk());
    }
    cerrarSesion(cookie).andExpect(status().isNoContent());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ? OR usuario_id = ?",
                Integer.class,
                carlos,
                carlos))
        .isEqualTo(antes);
  }
}
