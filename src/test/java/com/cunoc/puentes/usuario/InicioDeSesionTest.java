package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.Application;
import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** US-P2-06: inicio de sesión y bloqueo por intentos, con PostgreSQL real. */
@SpringBootTest
// Sin imprimir: MockMvc volcaría al log el cuerpo de cada petición, contraseña incluida
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class InicioDeSesionTest extends PruebaIntegracion {

  private static final String DOMINIO = "@login-prueba.com";
  private static final String CORREO = "carlos" + DOMINIO;
  private static final String CONTRASENA = "Puentes2026ok";
  private static final String INCORRECTA = "ContrasenaIncorrecta9";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;
  @Autowired AutenticacionService autenticacion;

  private UUID carlos;

  @BeforeEach
  void crearCuenta() {
    limpiar();
    carlos = guardar(CORREO, EstadoUsuario.ACTIVO).getId();
  }

  @AfterEach
  void limpiar() {
    String cuentas = "SELECT id FROM usuario WHERE correo LIKE '%" + DOMINIO + "'";
    jdbc.update("DELETE FROM auditoria WHERE entidad_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM token_renovacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%" + DOMINIO + "'");
  }

  private Usuario guardar(String correo, EstadoUsuario estado) {
    Usuario cuenta = Cuentas.estudiante(correo);
    cuenta.setNombre("Carlos Ruiz");
    cuenta.setContrasenaHash(codificador.encode(CONTRASENA));
    cuenta.setEstado(estado);
    if (estado == EstadoUsuario.INACTIVO) {
      cuenta.setDesactivadoEn(Instant.now());
      cuenta.setMotivoBaja("Baja de prueba");
    }
    if (estado == EstadoUsuario.PENDIENTE) {
      cuenta.setCorreoVerificadoEn(null);
    }
    return usuarios.save(cuenta);
  }

  private ResultActions login(String correo, String contrasena) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"correo": "%s", "contrasena": "%s"}
                """
                    .formatted(correo, contrasena)));
  }

  private void fallar(int veces) throws Exception {
    for (int i = 0; i < veces; i++) {
      login(CORREO, INCORRECTA).andExpect(status().isUnauthorized());
    }
  }

  private Map<String, Object> intentos() {
    return jdbc.queryForMap(
        "SELECT intentos_fallidos, primer_fallo_en, bloqueado_hasta FROM usuario WHERE id = ?",
        carlos);
  }

  private static String sha256(String texto) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
  }

  private static String valorDeLaCookie(MvcResult resultado) {
    String cookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).startsWith("refresh_token=");
    return cookie.substring("refresh_token=".length(), cookie.indexOf(';'));
  }

  private int auditoriasDeBloqueo() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auditoria WHERE accion = 'BLOQUEAR_CUENTA' AND entidad_id = ?",
        Integer.class,
        carlos);
  }

  @Test
  void iniciaSesionConTokenDeAccesoYCookieDeRenovacion() throws Exception {
    MvcResult resultado =
        login(CORREO, CONTRASENA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(900))
            .andExpect(jsonPath("$.usuario.id").value(carlos.toString()))
            .andExpect(jsonPath("$.usuario.nombre").value("Carlos Ruiz"))
            .andExpect(jsonPath("$.usuario.rol").value("ESTUDIANTE"))
            .andReturn();

    // El token de acceso es un JWT cuyo sub es el usuario y vive 15 minutos
    String jwt = com.jayway.jsonpath.JsonPath.read(contenido(resultado), "$.access_token");
    String carga =
        new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(carga).contains("\"sub\":\"" + carlos + "\"").contains("\"rol\":\"ESTUDIANTE\"");

    String cookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie)
        .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=604800");
  }

  private static String contenido(MvcResult resultado) throws Exception {
    return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void guardaElTokenDeRenovacionSoloComoHashYNuncaEnLosLogs(CapturedOutput salida)
      throws Exception {
    MvcResult resultado = login(CORREO, CONTRASENA).andExpect(status().isOk()).andReturn();
    String renovacion = valorDeLaCookie(resultado);

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT token_hash, revocado_en,"
                + " EXTRACT(EPOCH FROM (expira_en - now())) AS segundos"
                + " FROM token_renovacion WHERE usuario_id = ?",
            carlos);
    assertThat(fila.get("token_hash")).isEqualTo(sha256(renovacion));
    assertThat(fila.get("revocado_en")).isNull();
    assertThat(((Number) fila.get("segundos")).doubleValue())
        .isBetween(7 * 86_400.0 - 60, 7 * 86_400.0 + 60);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM token_renovacion WHERE token_hash = ?",
                Integer.class,
                renovacion))
        .isZero();
    assertThat(salida.getAll()).doesNotContain(renovacion).doesNotContain(CONTRASENA);
  }

  @Test
  void cadaInicioDeSesionCreaUnTokenNuevoYPermiteVariasSesiones() throws Exception {
    String primero = valorDeLaCookie(login(CORREO, CONTRASENA).andReturn());
    String segundo = valorDeLaCookie(login(CORREO, CONTRASENA).andReturn());

    assertThat(segundo).isNotEqualTo(primero);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM token_renovacion WHERE usuario_id = ?",
                Integer.class,
                carlos))
        .isEqualTo(2);
  }

  @Test
  void elCorreoNoDistingueMayusculas() throws Exception {
    login("CARLOS@Login-Prueba.com", CONTRASENA).andExpect(status().isOk());
  }

  @Test
  void correoInexistenteYContrasenaIncorrectaDanLaMismaRespuesta(CapturedOutput salida)
      throws Exception {
    String desconocido =
        contenido(
            login("nadie" + DOMINIO, INCORRECTA).andExpect(status().isUnauthorized()).andReturn());
    String mala =
        contenido(login(CORREO, INCORRECTA).andExpect(status().isUnauthorized()).andReturn());

    assertThat(desconocido).isEqualTo(mala).contains("credenciales-invalidas");
    assertThat(salida.getAll()).doesNotContain(INCORRECTA);
    assertThat(desconocido).doesNotContain(INCORRECTA);
  }

  @Test
  void cadaFalloSumaUnIntentoYTresNoBloquean() throws Exception {
    fallar(3);

    Map<String, Object> fila = intentos();
    assertThat(fila.get("intentos_fallidos")).isEqualTo(3);
    assertThat(fila.get("primer_fallo_en")).isNotNull();
    assertThat(fila.get("bloqueado_hasta")).isNull();
    login(CORREO, CONTRASENA).andExpect(status().isOk());
  }

  @Test
  void elQuintoFalloBloqueaQuinceMinutosYQuedaAuditado() throws Exception {
    fallar(5);

    Instant bloqueadoHasta = ((Timestamp) intentos().get("bloqueado_hasta")).toInstant();
    assertThat(bloqueadoHasta)
        .isBetween(Instant.now().plusSeconds(14 * 60), Instant.now().plusSeconds(15 * 60 + 5));
    assertThat(auditoriasDeBloqueo()).isEqualTo(1);
    Map<String, Object> auditoria =
        jdbc.queryForMap(
            "SELECT usuario_id, valor_anterior ->> 'intentos_fallidos' AS antes,"
                + " valor_nuevo ->> 'intentos_fallidos' AS despues,"
                + " valor_anterior ->> 'bloqueado_hasta' AS bloqueo_antes,"
                + " valor_nuevo ->> 'bloqueado_hasta' AS bloqueo_despues"
                + " FROM auditoria WHERE accion = 'BLOQUEAR_CUENTA' AND entidad_id = ?",
            carlos);
    assertThat(auditoria.get("usuario_id")).isNull();
    assertThat(auditoria.get("antes")).isEqualTo("4");
    assertThat(auditoria.get("despues")).isEqualTo("5");
    assertThat(auditoria.get("bloqueo_antes")).isNull();
    assertThat(auditoria.get("bloqueo_despues")).isNotNull();
  }

  @Test
  void unaCuentaBloqueadaRechazaIncluidaLaContrasenaCorrecta() throws Exception {
    fallar(5);

    login(CORREO, CONTRASENA)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/cuenta-bloqueada")))
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.matchesPattern(".*\\b1[45] minutos.*")));
  }

  @Test
  void siguenBloqueadosMientrasDuraElBloqueoSinExtenderlo() throws Exception {
    fallar(5);
    Object antes = intentos().get("bloqueado_hasta");

    login(CORREO, INCORRECTA).andExpect(status().isUnauthorized());

    assertThat(intentos().get("bloqueado_hasta")).isEqualTo(antes);
    assertThat(intentos().get("intentos_fallidos")).isEqualTo(5);
    assertThat(auditoriasDeBloqueo()).isEqualTo(1);
  }

  @Test
  void pasadosLosQuinceMinutosElAccesoSeRestablece() throws Exception {
    fallar(5);
    jdbc.update(
        "UPDATE usuario SET bloqueado_hasta = now() - interval '1 minute',"
            + " primer_fallo_en = now() - interval '16 minutes' WHERE id = ?",
        carlos);

    login(CORREO, CONTRASENA).andExpect(status().isOk());

    Map<String, Object> fila = intentos();
    assertThat(fila.get("intentos_fallidos")).isEqualTo(0);
    assertThat(fila.get("primer_fallo_en")).isNull();
    assertThat(fila.get("bloqueado_hasta")).isNull();
  }

  @Test
  void trasUnBloqueoVencidoUnFalloEmpiezaUnaVentanaNueva() throws Exception {
    fallar(5);
    jdbc.update(
        "UPDATE usuario SET bloqueado_hasta = now() - interval '1 minute',"
            + " primer_fallo_en = now() - interval '16 minutes' WHERE id = ?",
        carlos);

    fallar(1);

    Map<String, Object> fila = intentos();
    assertThat(fila.get("intentos_fallidos")).isEqualTo(1);
    // El bloqueo viejo ya venció: no vuelve a regir
    assertThat(
            PoliticaDeBloqueo.estaBloqueada(
                ((Timestamp) fila.get("bloqueado_hasta")).toInstant(), Instant.now()))
        .isFalse();
    login(CORREO, CONTRASENA).andExpect(status().isOk());
  }

  @Test
  void losFallosFueraDeLaVentanaNoSeAcumulan() throws Exception {
    jdbc.update(
        "UPDATE usuario SET intentos_fallidos = 4,"
            + " primer_fallo_en = now() - interval '20 minutes' WHERE id = ?",
        carlos);

    fallar(1);

    Map<String, Object> fila = intentos();
    assertThat(fila.get("intentos_fallidos")).isEqualTo(1);
    assertThat(fila.get("bloqueado_hasta")).isNull();
    assertThat(((Timestamp) fila.get("primer_fallo_en")).toInstant())
        .isAfter(Instant.now().minusSeconds(60));
  }

  @Test
  void unFalloDentroDeLaVentanaSigueSumandoSobreElPrimero() throws Exception {
    jdbc.update(
        "UPDATE usuario SET intentos_fallidos = 4,"
            + " primer_fallo_en = now() - interval '10 minutes' WHERE id = ?",
        carlos);

    fallar(1);

    assertThat(intentos().get("intentos_fallidos")).isEqualTo(5);
    assertThat(intentos().get("bloqueado_hasta")).isNotNull();
  }

  @Test
  void unInicioDeSesionCorrectoReiniciaElContador() throws Exception {
    fallar(3);

    login(CORREO, CONTRASENA).andExpect(status().isOk());

    assertThat(intentos().get("intentos_fallidos")).isEqualTo(0);
    assertThat(intentos().get("primer_fallo_en")).isNull();
  }

  @Test
  void elBloqueoSobreviveAUnReinicioDelServidor() throws Exception {
    fallar(5);

    // Una segunda aplicación contra la misma base es un "reinicio": no comparte memoria con la
    // primera
    try (ConfigurableApplicationContext reiniciada = arrancarOtraInstancia()) {
      assertThatThrownBy(
              () ->
                  reiniciada.getBean(AutenticacionService.class).iniciarSesion(CORREO, CONTRASENA))
          .isInstanceOf(NegocioException.class)
          .satisfies(
              e ->
                  assertThat(((NegocioException) e).getBody().getType().toString())
                      .endsWith("/cuenta-bloqueada"));
    }
  }

  private static ConfigurableApplicationContext arrancarOtraInstancia() {
    // Argumentos de línea de comandos: pesan más que los ${VARIABLE} de application.properties
    return new SpringApplicationBuilder(Application.class)
        .web(WebApplicationType.SERVLET)
        .run(
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.flyway.user=" + POSTGRES.getUsername(),
            "--spring.flyway.password=" + POSTGRES.getPassword(),
            "--sgp.jwt.secreto=clave-de-pruebas-de-al-menos-32-bytes!",
            "--sgp.minio.url=" + MINIO.getS3URL(),
            "--sgp.minio.url-publica=" + MINIO.getS3URL(),
            "--sgp.minio.usuario=" + MINIO.getUserName(),
            "--sgp.minio.clave=" + MINIO.getPassword(),
            "--server.port=0",
            "--management.server.port=0");
  }

  @Test
  void diezIntentosSimultaneosNoPierdenCuentasYBloquean() throws Exception {
    int hilos = 10;
    ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
    CountDownLatch salida = new CountDownLatch(1);
    try {
      List<Future<?>> intentos =
          java.util.stream.IntStream.range(0, hilos)
              .mapToObj(
                  i ->
                      ejecutor.submit(
                          () -> {
                            salida.await();
                            assertThatThrownBy(
                                    () -> autenticacion.iniciarSesion(CORREO, INCORRECTA))
                                .isInstanceOf(NegocioException.class);
                            return null;
                          }))
              .collect(java.util.stream.Collectors.toList());
      salida.countDown();
      for (Future<?> intento : intentos) {
        intento.get(60, TimeUnit.SECONDS);
      }
    } finally {
      ejecutor.shutdownNow();
    }

    Map<String, Object> fila = intentos();
    assertThat(fila.get("intentos_fallidos")).isEqualTo(hilos);
    assertThat(fila.get("bloqueado_hasta")).isNotNull();
    assertThat(auditoriasDeBloqueo()).isEqualTo(1);
  }

  @Test
  void soloLasCuentasActivasIniciaSesion() throws Exception {
    guardar("pendiente" + DOMINIO, EstadoUsuario.PENDIENTE);
    guardar("inactiva" + DOMINIO, EstadoUsuario.INACTIVO);

    login("pendiente" + DOMINIO, CONTRASENA)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/cuenta-pendiente")));
    login("inactiva" + DOMINIO, CONTRASENA)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/cuenta-inactiva")));
  }

  @Test
  void elEstadoDeLaCuentaNoSeRevelaSinLaContrasenaCorrecta() throws Exception {
    guardar("pendiente" + DOMINIO, EstadoUsuario.PENDIENTE);

    String pendiente =
        contenido(
            login("pendiente" + DOMINIO, INCORRECTA)
                .andExpect(status().isUnauthorized())
                .andReturn());

    assertThat(pendiente).contains("credenciales-invalidas").doesNotContain("pendiente");
  }

  @Test
  void lasRutasDeAutenticacionNoNecesitanSesion() throws Exception {
    // Sin Authorization: un 401 de la cadena de filtros no traería el tipo de error de negocio
    login("nadie" + DOMINIO, INCORRECTA)
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/credenciales-invalidas")));
  }

  @Test
  void unaContrasenaDemasiadoLargaSeTrataComoIncorrecta() throws Exception {
    login(CORREO, "a1".repeat(60)).andExpect(status().isUnauthorized());
    assertThat(intentos().get("intentos_fallidos")).isEqualTo(1);
  }
}
