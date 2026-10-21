package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** RN-USR-08, US-P2-12: listado, baja lógica y reactivación, con sesiones reales. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AdministracionUsuariosTest extends PruebaIntegracion {

  private static final String DOMINIO = "@admin-usuarios-prueba.com";
  private static final String CONTRASENA = "Puentes2026ok";
  private static final String MOTIVO = "Ya no pertenece al curso";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;

  private Usuario admin;

  @BeforeEach
  void preparar() {
    borrarDatosDePrueba();
    admin = guardar(Cuentas.administrador("admin" + DOMINIO));
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

  private Usuario guardar(Usuario cuenta) {
    cuenta.setNombre(
        cuenta.getNombre().contains("@")
            ? nombreSinCorreo(cuenta.getCorreo())
            : cuenta.getNombre());
    cuenta.setContrasenaHash(codificador.encode(CONTRASENA));
    return usuarios.saveAndFlush(cuenta);
  }

  private static String nombreSinCorreo(String correo) {
    return correo.startsWith("admin")
        ? "Admin Prueba"
        : "Persona " + correo.substring(0, correo.indexOf('@'));
  }

  private ResultActions baja(UUID id, String motivo) throws Exception {
    return mvc.perform(
        post("/api/v1/usuarios/" + id + "/baja")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"motivo\":\"%s\"}".formatted(motivo))
            .with(como(Rol.ADMINISTRADOR, admin.getId())));
  }

  private ResultActions reactivar(UUID id) throws Exception {
    return mvc.perform(
        post("/api/v1/usuarios/" + id + "/reactivacion")
            .with(como(Rol.ADMINISTRADOR, admin.getId())));
  }

  private MvcResult iniciarSesion(String correo) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, CONTRASENA)))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  void listaFiltraYNoExponeElHashNiAceptaUnaBusquedaLarga() throws Exception {
    Usuario ana = Cuentas.estudiante("ana.perez" + DOMINIO);
    ana.setNombre("Ana Perez");
    guardar(ana);
    Usuario carlos = Cuentas.estudiante("carlos" + DOMINIO);
    carlos.setNombre("Carlos Ruiz");
    guardar(carlos);
    Usuario docente = Cuentas.estudiante("docente" + DOMINIO);
    docente.setRol(Rol.CATEDRATICO);
    docente.setNombre("Docente Uno");
    guardar(docente);

    mvc.perform(
            get("/api/v1/usuarios")
                .param("rol", "ESTUDIANTE")
                .param("q", "ana")
                .param("size", "500")
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].nombre").value("Ana Perez"))
        .andExpect(jsonPath("$.content[0].contrasena_hash").doesNotExist())
        .andExpect(jsonPath("$.content[0].intentos_fallidos").doesNotExist());

    mvc.perform(
            get("/api/v1/usuarios")
                .param("estado", "ACTIVO")
                .param("rol", "CATEDRATICO")
                .param("q", "Docente Uno")
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].correo").value("docente" + DOMINIO));

    mvc.perform(get("/api/v1/usuarios/" + ana.getId()).with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ana.getId().toString()))
        .andExpect(jsonPath("$.contrasena_hash").doesNotExist());

    mvc.perform(
            get("/api/v1/usuarios")
                .param("q", "x".repeat(121))
                .with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(endsWith("/busqueda-invalida")));
  }

  @Test
  void meSigueSiendoElPerfilPropioAunqueExistaGetPorId() throws Exception {
    mvc.perform(get("/api/v1/usuarios/me").with(como(Rol.ADMINISTRADOR, admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(admin.getId().toString()))
        .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
  }

  @Test
  void laBajaRevocaLasSesionesYElJwtDejaDeServir() throws Exception {
    Usuario estudiante = Cuentas.estudiante("carlos" + DOMINIO);
    estudiante.setNombre("Carlos Ruiz");
    guardar(estudiante);
    MvcResult login = iniciarSesion(estudiante.getCorreo());
    String acceso = JsonPath.read(login.getResponse().getContentAsString(), "$.access_token");
    String cookie =
        login
            .getResponse()
            .getHeader(HttpHeaders.SET_COOKIE)
            .substring("refresh_token=".length())
            .split(";", 2)[0];

    baja(estudiante.getId(), MOTIVO)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("INACTIVO"))
        .andExpect(jsonPath("$.motivo_baja").value(MOTIVO));

    assertThat(usuarios.findById(estudiante.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoUsuario.INACTIVO);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM token_renovacion WHERE usuario_id = ? AND revocado_en IS NULL",
                Integer.class,
                estudiante.getId()))
        .isZero();

    mvc.perform(post("/api/v1/auth/renovar").cookie(new Cookie("refresh_token", cookie)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/sesion-invalida")));
    mvc.perform(get("/api/v1/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void noSeDaDeBajaASiMismoNiAUnaCuentaYaInactivaNiAlUltimoAdministrador() throws Exception {
    baja(admin.getId(), MOTIVO)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(endsWith("/baja-propia-no-permitida")));

    Usuario inactivo = Cuentas.estudiante("baja" + DOMINIO);
    inactivo.setNombre("Ya Inactivo");
    inactivo.setEstado(EstadoUsuario.INACTIVO);
    inactivo.setDesactivadoEn(Instant.now());
    inactivo.setMotivoBaja("Baja previa");
    guardar(inactivo);
    baja(inactivo.getId(), MOTIVO)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(endsWith("/cuenta-ya-inactiva")));

    List<UUID> ajenos =
        jdbc.queryForList(
            "SELECT id FROM usuario WHERE rol = 'ADMINISTRADOR' AND estado = 'ACTIVO' AND correo NOT LIKE ?",
            UUID.class,
            "%" + DOMINIO);
    for (UUID id : ajenos) {
      jdbc.update(
          "UPDATE usuario SET estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'tmp' WHERE id = ?",
          id);
    }
    try {
      Usuario victima = guardar(Cuentas.administrador("victima" + DOMINIO));
      jdbc.update(
          "UPDATE usuario SET estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'tmp' WHERE id = ?",
          admin.getId());
      baja(victima.getId(), MOTIVO)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value(endsWith("/ultimo-administrador")));
      assertThat(usuarios.findById(victima.getId()).orElseThrow().getEstado())
          .isEqualTo(EstadoUsuario.ACTIVO);
    } finally {
      jdbc.update(
          "UPDATE usuario SET estado = 'ACTIVO', desactivado_en = NULL, motivo_baja = NULL,"
              + " desactivado_por = NULL WHERE id = ?",
          admin.getId());
      for (UUID id : ajenos) {
        jdbc.update(
            "UPDATE usuario SET estado = 'ACTIVO', desactivado_en = NULL, motivo_baja = NULL,"
                + " desactivado_por = NULL WHERE id = ?",
            id);
      }
    }
  }

  @Test
  void reactivarVuelveAPendienteOActivoSegunLoQueFalte() throws Exception {
    Usuario sinCorreo = Cuentas.estudiante("pendiente" + DOMINIO);
    sinCorreo.setNombre("Sin Correo");
    sinCorreo.setEstado(EstadoUsuario.PENDIENTE);
    sinCorreo.setCorreoVerificadoEn(null);
    guardar(sinCorreo);
    baja(sinCorreo.getId(), MOTIVO).andExpect(status().isOk());
    reactivar(sinCorreo.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.desactivado_en").isEmpty());
    Usuario reactivado = usuarios.findById(sinCorreo.getId()).orElseThrow();
    assertThat(reactivado.getDesactivadoEn()).isNull();
    assertThat(reactivado.getMotivoBaja()).isNull();

    Usuario profesional = Cuentas.estudiante("pro" + DOMINIO);
    profesional.setNombre("Pro Verificado");
    profesional.setRol(Rol.PROFESIONAL_EXTERNO);
    profesional.setNumeroColegiado("CIG-5511");
    profesional.setColegiadoVerificadoEn(Cuentas.AHORA);
    profesional.setColegiadoVerificadoPor(admin);
    guardar(profesional);
    baja(profesional.getId(), MOTIVO).andExpect(status().isOk());
    reactivar(profesional.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ACTIVO"));

    reactivar(profesional.getId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(endsWith("/cuenta-no-inactiva")));
  }

  @Test
  void reactivarChocaSiElColegiadoYaLoTieneOtraCuentaViva() throws Exception {
    Usuario vivo = Cuentas.estudiante("vivo" + DOMINIO);
    vivo.setNombre("Vivo");
    vivo.setRol(Rol.PROFESIONAL_EXTERNO);
    vivo.setNumeroColegiado("CIG-DUP");
    vivo.setColegiadoVerificadoEn(Cuentas.AHORA);
    vivo.setColegiadoVerificadoPor(admin);
    guardar(vivo);

    Usuario inactivo = Cuentas.estudiante("inactivo" + DOMINIO);
    inactivo.setNombre("Inactivo");
    inactivo.setRol(Rol.PROFESIONAL_EXTERNO);
    inactivo.setNumeroColegiado("CIG-DUP");
    inactivo.setEstado(EstadoUsuario.INACTIVO);
    inactivo.setDesactivadoEn(Instant.now());
    inactivo.setMotivoBaja(MOTIVO);
    guardar(inactivo);

    reactivar(inactivo.getId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(endsWith("/colegiado-duplicado")));
  }

  @Test
  void auditaLaBajaYLaReactivacionSinHash() throws Exception {
    Usuario estudiante = Cuentas.estudiante("auditada" + DOMINIO);
    estudiante.setNombre("Auditada");
    guardar(estudiante);
    baja(estudiante.getId(), MOTIVO).andExpect(status().isOk());
    reactivar(estudiante.getId()).andExpect(status().isOk());

    List<String> acciones =
        jdbc.queryForList(
            "SELECT accion FROM auditoria WHERE entidad_id = ? ORDER BY creado_en",
            String.class,
            estudiante.getId());
    assertThat(acciones).containsExactly("DAR_DE_BAJA", "REACTIVAR");
    String valores =
        jdbc.queryForObject(
            "SELECT string_agg(valor_nuevo::text, ' ') FROM auditoria WHERE entidad_id = ?",
            String.class,
            estudiante.getId());
    assertThat(valores).doesNotContain(CONTRASENA).doesNotContain("$2a$");
  }
}
