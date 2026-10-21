package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.CorreosDePrueba.Buzon;
import com.cunoc.puentes.usuario.CorreosDePrueba.Correo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** DT-SEC-07: recuperación pública; una cuenta inactiva no recibe correo. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(CorreosDePrueba.class)
@ExtendWith(OutputCaptureExtension.class)
class RecuperacionContrasenaTest extends PruebaIntegracion {

  private static final String DOMINIO = "@recuperacion-prueba.com";
  private static final String CORREO = "ana" + DOMINIO;
  private static final String ACTUAL = "Puentes2026ok";
  private static final String NUEVA = "Puentes2027ok";
  private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;
  @Autowired Buzon buzon;

  @BeforeEach
  @AfterEach
  void limpiar() {
    buzon.vaciar();
    String cuentas = "SELECT id FROM usuario WHERE correo LIKE '%" + DOMINIO + "'";
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad_id IN ("
            + cuentas
            + ") OR usuario_id IN ("
            + cuentas
            + ")");
    jdbc.update("DELETE FROM token_verificacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM token_renovacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%" + DOMINIO + "'");
  }

  private static MockHttpServletRequestBuilder json(String ruta, String cuerpo) {
    return post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  private ResultActions solicitar(String correo) throws Exception {
    return mvc.perform(
        json("/api/v1/auth/recuperar-contrasena", "{\"correo\": \"%s\"}".formatted(correo)));
  }

  private ResultActions restablecer(String token, String contrasena) throws Exception {
    return mvc.perform(
        json(
            "/api/v1/auth/restablecer-contrasena",
            "{\"token\": \"%s\", \"contrasena\": \"%s\"}".formatted(token, contrasena)));
  }

  private ResultActions login(String correo, String contrasena) throws Exception {
    return mvc.perform(
        json(
            "/api/v1/auth/login",
            "{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(correo, contrasena)));
  }

  private Usuario guardarActiva(String correo) {
    Usuario cuenta = Cuentas.estudiante(correo);
    cuenta.setNombre("Ana Perez");
    cuenta.setContrasenaHash(codificador.encode(ACTUAL));
    return usuarios.saveAndFlush(cuenta);
  }

  private String tokenDelCorreo() throws Exception {
    Correo correo = buzon.siguiente(5000);
    assertThat(correo).as("correo de recuperación").isNotNull();
    Matcher enlace = TOKEN.matcher(correo.texto());
    assertThat(enlace.find()).isTrue();
    return enlace.group(1);
  }

  private static String sha256(String texto) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void solicitaElEnlaceYRestableceLaContrasena(CapturedOutput salida) throws Exception {
    Usuario ana = guardarActiva(CORREO);

    solicitar(CORREO.toUpperCase()).andExpect(status().isAccepted());
    Correo enviado = buzon.siguiente(5000);
    assertThat(enviado).isNotNull();
    assertThat(enviado.destinatario()).isEqualTo(CORREO);
    Matcher enlace = TOKEN.matcher(enviado.texto());
    assertThat(enlace.find()).isTrue();
    String token = enlace.group(1);
    assertThat(enviado.texto()).contains("/restablecer-contrasena?token=" + token);
    assertThat(token).hasSize(43);

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT token_hash, tipo, usado_en,"
                + " EXTRACT(EPOCH FROM (expira_en - creado_en)) AS segundos"
                + " FROM token_verificacion WHERE usuario_id = ?",
            ana.getId());
    assertThat(fila.get("token_hash")).isEqualTo(sha256(token));
    assertThat(fila.get("tipo")).isEqualTo("RECUPERACION_CONTRASENA");
    assertThat(fila.get("usado_en")).isNull();
    assertThat(((Number) fila.get("segundos")).doubleValue()).isBetween(3_590.0, 3_600.0);

    restablecer(token, NUEVA).andExpect(status().isNoContent());
    login(CORREO, ACTUAL).andExpect(status().isUnauthorized());
    login(CORREO, NUEVA).andExpect(status().isOk());

    String hash = usuarios.findById(ana.getId()).orElseThrow().getContrasenaHash();
    assertThat(salida.getAll()).doesNotContain(token, ACTUAL, NUEVA, hash);
  }

  @Test
  void elSegundoUsoDelMismoTokenResponde422() throws Exception {
    guardarActiva(CORREO);
    solicitar(CORREO).andExpect(status().isAccepted());
    String token = tokenDelCorreo();

    restablecer(token, NUEVA).andExpect(status().isNoContent());
    restablecer(token, "OtraClave2028x")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(endsWith("/token-invalido")));

    login(CORREO, NUEVA).andExpect(status().isOk());
  }

  @Test
  void unaCuentaInactivaOInexistenteResponde202SinEnviarCorreo() throws Exception {
    Usuario inactiva = Cuentas.estudiante("baja" + DOMINIO);
    inactiva.setNombre("Ya Inactiva");
    inactiva.setContrasenaHash(codificador.encode(ACTUAL));
    inactiva.setEstado(EstadoUsuario.INACTIVO);
    inactiva.setDesactivadoEn(Instant.now());
    inactiva.setMotivoBaja("Baja de prueba");
    usuarios.saveAndFlush(inactiva);

    solicitar("nadie" + DOMINIO).andExpect(status().isAccepted());
    solicitar(inactiva.getCorreo()).andExpect(status().isAccepted());
    assertThat(buzon.siguiente(500)).isNull();
  }

  @Test
  void unaSolicitudNuevaAnulaElTokenAnterior() throws Exception {
    guardarActiva(CORREO);
    solicitar(CORREO).andExpect(status().isAccepted());
    String anterior = tokenDelCorreo();

    solicitar(CORREO).andExpect(status().isAccepted());
    String nuevo = tokenDelCorreo();
    assertThat(nuevo).isNotEqualTo(anterior);

    restablecer(anterior, NUEVA)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(endsWith("/token-invalido")));
    restablecer(nuevo, NUEVA).andExpect(status().isNoContent());
  }

  @Test
  void restablecerReiniciaElBloqueoYAuditaSinSecretos() throws Exception {
    Usuario ana = guardarActiva(CORREO);
    jdbc.update(
        "UPDATE usuario SET intentos_fallidos = 4, primer_fallo_en = now(),"
            + " bloqueado_hasta = now() + interval '10 minutes' WHERE id = ?",
        ana.getId());

    solicitar(CORREO).andExpect(status().isAccepted());
    String token = tokenDelCorreo();
    restablecer(token, NUEVA).andExpect(status().isNoContent());

    Usuario despues = usuarios.findById(ana.getId()).orElseThrow();
    assertThat(despues.getIntentosFallidos()).isZero();
    assertThat(despues.getBloqueadoHasta()).isNull();

    List<String> acciones =
        jdbc.queryForList(
            "SELECT accion FROM auditoria WHERE entidad_id = ? ORDER BY creado_en",
            String.class,
            ana.getId());
    assertThat(acciones).containsExactly("MODIFICAR");
    String valores =
        jdbc.queryForObject(
            "SELECT string_agg(coalesce(valor_anterior::text, '') || ' ' || coalesce(valor_nuevo::text, ''), ' ')"
                + " FROM auditoria WHERE entidad_id = ?",
            String.class,
            ana.getId());
    assertThat(valores).doesNotContain(ACTUAL, NUEVA, token, "$2a$");
  }
}
