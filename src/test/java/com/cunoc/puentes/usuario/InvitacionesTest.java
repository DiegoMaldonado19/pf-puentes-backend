package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.CorreosDePrueba.Buzon;
import com.cunoc.puentes.usuario.CorreosDePrueba.Correo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** RN-USR-03: invitaciones de Administrador a Catedrático, Profesional externo y Administrador. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CorreosDePrueba.class)
class InvitacionesTest extends PruebaIntegracion {

  private static final String DOMINIO = "@invitacion-prueba.com";
  private static final String CONTRASENA = "Puentes2026ok";
  private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired InvitacionRepository invitaciones;
  @Autowired Buzon buzon;

  private Usuario admin;

  @BeforeEach
  void preparar() {
    borrarDatosDePrueba();
    admin = usuarios.saveAndFlush(Cuentas.administrador("admin" + DOMINIO));
  }

  @AfterEach
  void limpiar() {
    borrarDatosDePrueba();
  }

  private void borrarDatosDePrueba() {
    buzon.vaciar();
    String cuentas = "SELECT id FROM usuario WHERE correo LIKE '%" + DOMINIO + "'";
    String invitadas = "SELECT id FROM invitacion WHERE correo LIKE '%" + DOMINIO + "'";
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad_id IN ("
            + invitadas
            + ") OR entidad_id IN ("
            + cuentas
            + ") OR usuario_id IN ("
            + cuentas
            + ")");
    jdbc.update(
        "DELETE FROM invitacion WHERE correo LIKE '%"
            + DOMINIO
            + "' OR administrador_id IN ("
            + cuentas
            + ")");
    jdbc.update("DELETE FROM token_renovacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%" + DOMINIO + "'");
  }

  private static MockHttpServletRequestBuilder json(String ruta, String cuerpo) {
    return post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  private ResultActions invitar(String correo, String rol) throws Exception {
    return mvc.perform(
        json("/api/v1/invitaciones", "{\"correo\":\"%s\",\"rol\":\"%s\"}".formatted(correo, rol))
            .with(como(Rol.ADMINISTRADOR, admin.getId())));
  }

  private ResultActions aceptar(String token, String nombre, String colegiado) throws Exception {
    String cuerpo =
        colegiado == null
            ? "{\"token\":\"%s\",\"nombre\":\"%s\",\"contrasena\":\"%s\"}"
                .formatted(token, nombre, CONTRASENA)
            : "{\"token\":\"%s\",\"nombre\":\"%s\",\"contrasena\":\"%s\",\"numero_colegiado\":\"%s\"}"
                .formatted(token, nombre, CONTRASENA, colegiado);
    return mvc.perform(json("/api/v1/auth/invitaciones/aceptar", cuerpo));
  }

  private String tokenDelCorreo() throws Exception {
    Correo correo = buzon.siguiente(5000);
    assertThat(correo).as("correo de invitación").isNotNull();
    Matcher enlace = TOKEN.matcher(correo.texto());
    assertThat(enlace.find()).isTrue();
    return enlace.group(1);
  }

  @Test
  void invitaAUnCatedraticoEnviaElEnlaceYAlAceptarNaceActivoConCorreoVerificado() throws Exception {
    String correo = "catedratico" + DOMINIO;
    invitar(correo, "CATEDRATICO")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("ABIERTA"))
        .andExpect(jsonPath("$.rol").value("CATEDRATICO"))
        .andExpect(jsonPath("$.token").doesNotExist());

    Correo mensaje = buzon.siguiente(5000);
    assertThat(mensaje).isNotNull();
    assertThat(mensaje.destinatario()).isEqualTo(correo);
    Matcher enlace = TOKEN.matcher(mensaje.texto());
    assertThat(enlace.find()).isTrue();
    String token = enlace.group(1);
    assertThat(mensaje.texto()).contains("/aceptar-invitacion?token=" + token);
    assertThat(token).hasSize(43);

    mvc.perform(json("/api/v1/auth/invitaciones/consultar", "{\"token\":\"%s\"}".formatted(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correo").value(correo))
        .andExpect(jsonPath("$.rol").value("CATEDRATICO"));

    aceptar(token, "Ana Catedrática", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rol").value("CATEDRATICO"))
        .andExpect(jsonPath("$.estado").value("ACTIVO"))
        .andExpect(jsonPath("$.correo_verificado").value(true));

    Usuario cuenta = usuarios.buscarPorCorreo(correo).orElseThrow();
    assertThat(cuenta.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
    assertThat(cuenta.getCorreoVerificadoEn()).isNotNull();
    assertThat(cuenta.getContrasenaHash()).startsWith("$2a$12$").doesNotContain(CONTRASENA);
  }

  @Test
  void aceptarDosVecesRechazaLaSegundaConElMismoMensajeQueUnTokenInvalido() throws Exception {
    invitar("catedratico" + DOMINIO, "CATEDRATICO").andExpect(status().isCreated());
    String token = tokenDelCorreo();
    aceptar(token, "Ana", null).andExpect(status().isCreated());

    String segunda = cuerpoDeRechazo(token);
    String inexistente = cuerpoDeRechazo("token-que-nunca-existio");
    assertThat(segunda).isEqualTo(inexistente);
  }

  @Test
  void unTokenVencidoORevocadoSeRechazaIgualQueUnoInexistente() throws Exception {
    invitar("vencida" + DOMINIO, "CATEDRATICO").andExpect(status().isCreated());
    String vencido = tokenDelCorreo();
    jdbc.update(
        "UPDATE invitacion SET expira_en = now() - interval '1 hour' WHERE correo = ?",
        "vencida" + DOMINIO);

    invitar("revocada" + DOMINIO, "ADMINISTRADOR").andExpect(status().isCreated());
    String revocado = tokenDelCorreo();
    Invitacion abierta = invitaciones.buscarAbierta("revocada" + DOMINIO).orElseThrow();
    mvc.perform(
            post("/api/v1/invitaciones/" + abierta.getId() + "/revocacion")
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("REVOCADA"));

    List<String> cuerpos =
        List.of(cuerpoDeRechazo(vencido), cuerpoDeRechazo(revocado), cuerpoDeRechazo("no-existe"));
    assertThat(cuerpos).hasSize(3).containsOnly(cuerpos.getFirst());
  }

  @Test
  void elProfesionalExternoSinColegiadoSeRechazaYConColegiadoNacePendiente() throws Exception {
    invitar("profesional" + DOMINIO, "PROFESIONAL_EXTERNO").andExpect(status().isCreated());
    String token = tokenDelCorreo();

    aceptar(token, "Ana Profesional", null)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/colegiado-requerido")));
    assertThat(usuarios.existePorCorreo("profesional" + DOMINIO)).isFalse();

    aceptar(token, "Ana Profesional", "CIG-7788")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rol").value("PROFESIONAL_EXTERNO"))
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.correo_verificado").value(true));

    Usuario cuenta = usuarios.buscarPorCorreo("profesional" + DOMINIO).orElseThrow();
    assertThat(cuenta.getNumeroColegiado()).isEqualTo("CIG-7788");
    assertThat(cuenta.getColegiadoVerificadoEn()).isNull();
  }

  @Test
  void reinvitarUnaAbiertaVencidaRevocaLaAnteriorYCreaOtra() throws Exception {
    String correo = "reinvitada" + DOMINIO;
    invitar(correo, "CATEDRATICO").andExpect(status().isCreated());
    tokenDelCorreo();
    UUID anterior = invitaciones.buscarAbierta(correo).orElseThrow().getId();
    jdbc.update(
        "UPDATE invitacion SET expira_en = now() - interval '1 hour' WHERE id = ?", anterior);

    invitar(correo, "CATEDRATICO").andExpect(status().isCreated());
    tokenDelCorreo();

    Invitacion vieja = invitaciones.findById(anterior).orElseThrow();
    Invitacion nueva = invitaciones.buscarAbierta(correo).orElseThrow();
    assertThat(vieja.getRevocadaEn()).isNotNull();
    assertThat(vieja.getRevocadaPor().getId()).isEqualTo(admin.getId());
    assertThat(nueva.getId()).isNotEqualTo(anterior);
  }

  @Test
  void noInvitaAUnEstudianteNiAUnCorreoConCuentaOConInvitacionVigente() throws Exception {
    invitar("estudiante" + DOMINIO, "ESTUDIANTE")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/rol-no-invitable")));

    usuarios.saveAndFlush(Cuentas.estudiante("ya-existe" + DOMINIO));
    invitar("ya-existe" + DOMINIO, "CATEDRATICO")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/correo-ya-registrado")));

    invitar("vigente" + DOMINIO, "CATEDRATICO").andExpect(status().isCreated());
    tokenDelCorreo();
    invitar("vigente" + DOMINIO, "CATEDRATICO")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invitacion-abierta")));
  }

  @Test
  void listaPaginaYFiltraPorEstado() throws Exception {
    invitar("uno" + DOMINIO, "CATEDRATICO").andExpect(status().isCreated());
    tokenDelCorreo();
    invitar("dos" + DOMINIO, "ADMINISTRADOR").andExpect(status().isCreated());
    tokenDelCorreo();

    mvc.perform(get("/api/v1/invitaciones").with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(2))
        .andExpect(jsonPath("$.content[0].token_hash").doesNotExist());

    mvc.perform(
            get("/api/v1/invitaciones")
                .param("estado", "ABIERTA")
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(2));
  }

  @Test
  void auditaInvitarAceptarYRevocarSinTokenNiHashNiCorreoCompleto() throws Exception {
    invitar("auditada" + DOMINIO, "CATEDRATICO").andExpect(status().isCreated());
    String token = tokenDelCorreo();
    Invitacion invitacion = invitaciones.buscarAbierta("auditada" + DOMINIO).orElseThrow();
    aceptar(token, "Ana", null).andExpect(status().isCreated());

    invitar("revocable" + DOMINIO, "ADMINISTRADOR").andExpect(status().isCreated());
    tokenDelCorreo();
    UUID revocable = invitaciones.buscarAbierta("revocable" + DOMINIO).orElseThrow().getId();
    mvc.perform(
            post("/api/v1/invitaciones/" + revocable + "/revocacion")
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT accion, entidad, valor_anterior::text AS anterior, valor_nuevo::text AS nuevo"
                + " FROM auditoria WHERE entidad_id IN (?, ?, ?) OR usuario_id = ? ORDER BY creado_en",
            invitacion.getId(),
            revocable,
            usuarios.buscarPorCorreo("auditada" + DOMINIO).orElseThrow().getId(),
            admin.getId());

    assertThat(filas)
        .extracting(f -> f.get("accion") + ":" + f.get("entidad"))
        .contains(
            "INVITAR:invitacion",
            "CREAR:usuario",
            "MODIFICAR:invitacion",
            "REVOCAR_INVITACION:invitacion");
    String hash = invitacion.getTokenHash();
    assertThat(filas)
        .allSatisfy(
            f -> {
              String valores = String.valueOf(f.get("anterior")) + " " + f.get("nuevo");
              assertThat(valores)
                  .doesNotContain(CONTRASENA)
                  .doesNotContain(token)
                  .doesNotContain(hash)
                  .doesNotContain("auditada" + DOMINIO);
            });
  }

  private String cuerpoDeRechazo(String token) throws Exception {
    String cuerpo =
        aceptar(token, "Ana", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(
                jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invitacion-invalida")))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(cuerpo).doesNotContain(token);
    return cuerpo;
  }
}
