package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.CorreosDePrueba.Buzon;
import com.cunoc.puentes.usuario.CorreosDePrueba.Correo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US-P2-05: registro de estudiante y verificación del correo, de la petición a la base. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CorreosDePrueba.class)
@ExtendWith(OutputCaptureExtension.class)
class RegistroEstudianteTest extends PruebaIntegracion {

  private static final String DOMINIO = "@registro-prueba.com";
  private static final String CORREO = "ana" + DOMINIO;
  private static final String CONTRASENA = "Puentes2026ok";
  private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired Buzon buzon;

  @BeforeEach
  @AfterEach
  void limpiar() {
    buzon.vaciar();
    String cuentas = "SELECT id FROM usuario WHERE correo LIKE '%" + DOMINIO + "'";
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad = 'usuario' AND (entidad_id IN ("
            + cuentas
            + ") OR usuario_id IN ("
            + cuentas
            + "))");
    jdbc.update("DELETE FROM token_verificacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%" + DOMINIO + "'");
  }

  private static MockHttpServletRequestBuilder json(String ruta, String cuerpo) {
    return post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  private static String registro(String correo, String contrasena) {
    return """
        {"nombre": "Ana Pérez", "correo": "%s", "contrasena": "%s"}
        """
        .formatted(correo, contrasena);
  }

  private ResultActions registrar(String correo, String contrasena) throws Exception {
    return mvc.perform(json("/api/v1/auth/registro", registro(correo, contrasena)));
  }

  private ResultActions verificar(String token) throws Exception {
    return mvc.perform(
        json("/api/v1/auth/verificar-correo", "{\"token\": \"%s\"}".formatted(token)));
  }

  private ResultActions reenviar(String correo) throws Exception {
    return mvc.perform(
        json("/api/v1/auth/reenviar-verificacion", "{\"correo\": \"%s\"}".formatted(correo)));
  }

  /** El token que el estudiante recibe en el enlace del siguiente correo. */
  private String tokenDelCorreo() throws Exception {
    Correo correo = buzon.siguiente(5000);
    assertThat(correo).as("correo de verificación").isNotNull();
    Matcher enlace = TOKEN.matcher(correo.texto());
    assertThat(enlace.find()).isTrue();
    return enlace.group(1);
  }

  private Usuario cuenta(String correo) {
    return usuarios.buscarPorCorreo(correo).orElseThrow();
  }

  private static String sha256(String texto) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void registraUnEstudiantePendienteConElCorreoSinVerificarYSinFiltrarLaClave() throws Exception {
    registrar(CORREO, CONTRASENA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.correo_verificado").value(false))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(CONTRASENA))))
        .andExpect(jsonPath("$.contrasena").doesNotExist())
        .andExpect(jsonPath("$.contrasena_hash").doesNotExist());

    Usuario ana = cuenta(CORREO);
    assertThat(ana.getRol()).isEqualTo(Rol.ESTUDIANTE);
    assertThat(ana.getEstado()).isEqualTo(EstadoUsuario.PENDIENTE);
    assertThat(ana.getCorreoVerificadoEn()).isNull();
  }

  @Test
  void elVisitanteNoPuedeElegirSuRol() throws Exception {
    mvc.perform(
            json(
                "/api/v1/auth/registro",
                """
                {"nombre": "Ana", "correo": "%s", "contrasena": "%s", "rol": "ADMINISTRADOR"}
                """
                    .formatted(CORREO, CONTRASENA)))
        .andExpect(status().isCreated());

    assertThat(cuenta(CORREO).getRol()).isEqualTo(Rol.ESTUDIANTE);
  }

  @Test
  void unaSesionDeAdministradorTampocoEleva() throws Exception {
    UUID administrador = usuarios.save(Cuentas.administrador("admin" + DOMINIO)).getId();

    mvc.perform(
            json("/api/v1/auth/registro", registro(CORREO, CONTRASENA))
                .with(como(Rol.ADMINISTRADOR, administrador)))
        .andExpect(status().isCreated());

    assertThat(cuenta(CORREO).getRol()).isEqualTo(Rol.ESTUDIANTE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"corta1", "sinnumeroaqui", "1234567890"})
  void rechazaUnaContrasenaQueNoCumpleLaPolitica(String contrasena) throws Exception {
    registrar(CORREO, contrasena)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));

    assertThat(usuarios.existePorCorreo(CORREO)).isFalse();
  }

  @Test
  void guardaLaContrasenaSoloComoHashBcryptDeFactor12(CapturedOutput salida) throws Exception {
    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());
    tokenDelCorreo();

    String hash = cuenta(CORREO).getContrasenaHash();
    assertThat(hash).startsWith("$2a$12$").doesNotContain(CONTRASENA);
    assertThat(salida.getAll()).doesNotContain(CONTRASENA).doesNotContain(hash);
  }

  @Test
  void guardaElCorreoEnMinusculasYSinEspacios() throws Exception {
    registrar("  ANA" + DOMINIO.toUpperCase() + " ", CONTRASENA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.correo").value(CORREO));

    assertThat(cuenta(CORREO).getCorreo()).isEqualTo(CORREO);
  }

  @Test
  void rechazaUnCorreoSinFormatoValido() throws Exception {
    registrar("no-es-un-correo", CONTRASENA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("correo"));
  }

  @Test
  void noPermiteDosCuentasConElMismoCorreo() throws Exception {
    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());

    registrar("ANA" + DOMINIO, CONTRASENA)
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/correo-ya-registrado")))
        .andExpect(jsonPath("$.status").value(409));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE correo LIKE ?", Integer.class, "%" + DOMINIO))
        .isEqualTo(1);
  }

  @Test
  void emiteUnTokenDe24HorasYEnviaElEnlaceGuardandoSoloElHash() throws Exception {
    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());

    Correo correo = buzon.siguiente(5000);
    assertThat(correo).isNotNull();
    assertThat(correo.destinatario()).isEqualTo(CORREO);
    Matcher enlace = TOKEN.matcher(correo.texto());
    assertThat(enlace.find()).isTrue();
    String token = enlace.group(1);
    assertThat(correo.texto()).contains("/verificar-correo?token=" + token);
    assertThat(token).hasSize(43); // 256 bits en Base64 de URL

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT token_hash, tipo, usado_en,"
                + " EXTRACT(EPOCH FROM (expira_en - creado_en)) AS segundos"
                + " FROM token_verificacion WHERE usuario_id = ?",
            cuenta(CORREO).getId());
    assertThat(fila.get("token_hash")).isEqualTo(sha256(token));
    assertThat(fila.get("tipo")).isEqualTo("VERIFICACION_CORREO");
    assertThat(fila.get("usado_en")).isNull();
    assertThat(((Number) fila.get("segundos")).doubleValue()).isBetween(86_300.0, 86_400.0);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM token_verificacion WHERE token_hash = ?",
                Integer.class,
                token))
        .isZero();
  }

  @Test
  void verificarMarcaElCorreoYElTokenPeroNoActivaLaCuenta() throws Exception {
    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());
    String token = tokenDelCorreo();

    verificar(token).andExpect(status().isNoContent());

    Usuario ana = cuenta(CORREO);
    assertThat(ana.getCorreoVerificadoEn()).isNotNull();
    assertThat(ana.getEstado()).isEqualTo(EstadoUsuario.PENDIENTE);
    assertThat(
            jdbc.queryForObject(
                "SELECT usado_en FROM token_verificacion WHERE usuario_id = ?",
                java.sql.Timestamp.class,
                ana.getId()))
        .isNotNull();
  }

  @Test
  void unTokenVencidoYaUsadoOInexistenteSeRechazanConElMismoMensaje() throws Exception {
    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());
    String vencido = tokenDelCorreo();
    jdbc.update(
        "UPDATE token_verificacion SET creado_en = now() - interval '2 days',"
            + " expira_en = now() - interval '1 day' WHERE usuario_id = ?",
        cuenta(CORREO).getId());

    registrar("otra" + DOMINIO, CONTRASENA).andExpect(status().isCreated());
    String usado = tokenDelCorreo();
    verificar(usado).andExpect(status().isNoContent());

    List<String> detalles =
        List.of(
            cuerpoDeRechazo(vencido),
            cuerpoDeRechazo(usado),
            cuerpoDeRechazo("token-que-nunca-existio"));
    // Mismo cuerpo completo (tipo, título, detalle): no se distingue por qué falló
    assertThat(detalles).hasSize(3).containsOnly(detalles.getFirst());
  }

  /** Verifica que sea 422 token-invalido y devuelve el mensaje; los tres casos deben coincidir. */
  private String cuerpoDeRechazo(String token) throws Exception {
    String cuerpo =
        verificar(token)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/token-invalido")))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(cuerpo).doesNotContain(token);
    return cuerpo;
  }

  @Test
  void reenviarAnulaElTokenAnteriorYEnviaUnoNuevo() throws Exception {
    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());
    String anterior = tokenDelCorreo();

    reenviar(CORREO.toUpperCase()).andExpect(status().isAccepted());
    String nuevo = tokenDelCorreo();

    assertThat(nuevo).isNotEqualTo(anterior);
    verificar(anterior).andExpect(status().isUnprocessableEntity());
    verificar(nuevo).andExpect(status().isNoContent());
  }

  @Test
  void reenviarResponde202SinEnviarNadaSiLaCuentaNoExisteOYaVerifico() throws Exception {
    reenviar("nadie" + DOMINIO).andExpect(status().isAccepted());
    assertThat(buzon.siguiente(500)).isNull();

    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());
    verificar(tokenDelCorreo()).andExpect(status().isNoContent());

    reenviar(CORREO).andExpect(status().isAccepted());
    assertThat(buzon.siguiente(500)).isNull();
  }

  @Test
  void auditaElRegistroYLaVerificacionSinContrasenaNiToken() throws Exception {
    registrar(CORREO, CONTRASENA).andExpect(status().isCreated());
    String token = tokenDelCorreo();
    verificar(token).andExpect(status().isNoContent());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT accion, usuario_id, valor_anterior::text AS anterior, valor_nuevo::text AS nuevo"
                + " FROM auditoria WHERE entidad = 'usuario' AND entidad_id = ? ORDER BY creado_en",
            cuenta(CORREO).getId());

    assertThat(filas).extracting(f -> f.get("accion")).containsExactly("CREAR", "MODIFICAR");
    assertThat(filas).allSatisfy(f -> assertThat(f.get("usuario_id")).isNull());
    String hash = cuenta(CORREO).getContrasenaHash();
    assertThat(filas)
        .allSatisfy(
            f -> {
              String valores = f.get("anterior") + " " + f.get("nuevo");
              assertThat(valores)
                  .doesNotContain(CONTRASENA)
                  .doesNotContain(hash)
                  .doesNotContain(token);
            });
    assertThat(filas.get(1).get("nuevo").toString()).contains("correo_verificado_en");
  }
}
