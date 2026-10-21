package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** RN-USR-09: consulta de la bitácora con acciones reales y filtros. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(CorreosDePrueba.class)
class ConsultaAuditoriaTest extends PruebaIntegracion {

  private static final String DOMINIO = "@consulta-auditoria-prueba.com";
  private static final String CONTRASENA = "Puentes2026ok";
  private static final String BASE = "/api/v1/auditoria";
  private static final UUID LOTE = UUID.fromString("0192f5a0-aaaa-7000-8000-00000000c0de");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;

  private Usuario admin;

  @BeforeEach
  void preparar() {
    borrarDatosDePrueba();
    Usuario cuenta = Cuentas.administrador("admin" + DOMINIO);
    cuenta.setNombre("Admin Bitacora");
    cuenta.setContrasenaHash(codificador.encode(CONTRASENA));
    admin = usuarios.saveAndFlush(cuenta);
  }

  @AfterEach
  void limpiar() {
    borrarDatosDePrueba();
  }

  private void borrarDatosDePrueba() {
    String cuentas = "SELECT id FROM usuario WHERE correo LIKE '%" + DOMINIO + "'";
    String invitadas = "SELECT id FROM invitacion WHERE correo LIKE '%" + DOMINIO + "'";
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad_id IN ("
            + invitadas
            + ") OR entidad_id IN ("
            + cuentas
            + ") OR usuario_id IN ("
            + cuentas
            + ") OR entidad_id = ?",
        LOTE);
    jdbc.update(
        "DELETE FROM invitacion WHERE correo LIKE '%"
            + DOMINIO
            + "' OR administrador_id IN ("
            + cuentas
            + ")");
    jdbc.update("DELETE FROM token_verificacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM token_renovacion WHERE usuario_id IN (" + cuentas + ")");
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%" + DOMINIO + "'");
  }

  private MockHttpServletRequestBuilder bitacora() {
    return get(BASE).with(como(Rol.ADMINISTRADOR, admin.getId()));
  }

  @Test
  void filtraAccionesRealesYNoExponeHashNiToken() throws Exception {
    String correoEstudiante = "ana" + DOMINIO;
    mvc.perform(
            post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Ana Perez\",\"correo\":\"%s\",\"contrasena\":\"%s\"}"
                        .formatted(correoEstudiante, CONTRASENA)))
        .andExpect(status().isCreated());
    UUID estudianteId = usuarios.buscarPorCorreo(correoEstudiante).orElseThrow().getId();

    mvc.perform(
            post("/api/v1/invitaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"correo\":\"catedratico%s\",\"rol\":\"CATEDRATICO\"}".formatted(DOMINIO))
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isCreated());
    UUID invitacionId =
        jdbc.queryForObject(
            "SELECT id FROM invitacion WHERE correo = ?", UUID.class, "catedratico" + DOMINIO);

    mvc.perform(
            post("/api/v1/usuarios/" + estudianteId + "/baja")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Ya no pertenece al curso\"}")
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk());

    MvcResult porUsuario =
        mvc.perform(bitacora().param("usuario_id", admin.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].contrasena_hash").doesNotExist())
            .andExpect(jsonPath("$.content[0].token_hash").doesNotExist())
            .andReturn();
    String cuerpoUsuario = porUsuario.getResponse().getContentAsString();
    assertThat(cuerpoUsuario).doesNotContain(CONTRASENA).doesNotContain("$2a$");
    List<String> accionesDelAdmin = JsonPath.read(cuerpoUsuario, "$.content[*].accion");
    assertThat(accionesDelAdmin).contains("INVITAR", "DAR_DE_BAJA");

    mvc.perform(
            bitacora().param("entidad", "invitacion").param("entidad_id", invitacionId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].accion").value("INVITAR"));

    MvcResult delEstudiante =
        mvc.perform(
                bitacora().param("entidad", "usuario").param("entidad_id", estudianteId.toString()))
            .andExpect(status().isOk())
            .andReturn();
    String cuerpoEstudiante = delEstudiante.getResponse().getContentAsString();
    List<String> accionesDelEstudiante = JsonPath.read(cuerpoEstudiante, "$.content[*].accion");
    assertThat(accionesDelEstudiante).contains("CREAR", "DAR_DE_BAJA");
    assertThat(cuerpoEstudiante).doesNotContain("contrasena_hash").doesNotContain("token_hash");
  }

  @Test
  void recortaLaPaginaA100YOrdenaDeLaMasReciente() throws Exception {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    for (int i = 0; i < 101; i++) {
      insertar("MODIFICAR", base.plusSeconds(i));
    }

    mvc.perform(bitacora().param("entidad_id", LOTE.toString()).param("size", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.total_elements").value(101))
        .andExpect(jsonPath("$.content[0].creado_en").value(startsWith("2026-01-01T00:01:40")));
  }

  @Test
  void filtraPorRangoYRechazaUnDesdePosteriorAlHasta() throws Exception {
    Instant temprana = Instant.parse("2026-03-01T12:00:00Z");
    Instant tardia = Instant.parse("2026-03-02T12:00:00Z");
    insertar("CREAR", temprana);
    insertar("DAR_DE_BAJA", tardia);

    mvc.perform(
            bitacora().param("entidad_id", LOTE.toString()).param("desde", "2026-03-02T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].accion").value("DAR_DE_BAJA"));

    mvc.perform(
            bitacora().param("entidad_id", LOTE.toString()).param("hasta", "2026-03-01T18:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].accion").value("CREAR"));

    mvc.perform(
            bitacora()
                .param("desde", "2026-10-21T00:00:00Z")
                .param("hasta", "2026-10-20T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(endsWith("/rango-invalido")));
  }

  private void insertar(String accion, Instant creadoEn) {
    jdbc.update(
        """
        INSERT INTO auditoria (id, accion, entidad, entidad_id, valor_nuevo, creado_en, actualizado_en)
        VALUES (?, ?, 'usuario', ?, '{}'::jsonb, ?, ?)
        """,
        UUID.randomUUID(),
        accion,
        LOTE,
        java.sql.Timestamp.from(creadoEn),
        java.sql.Timestamp.from(creadoEn));
  }
}
