package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** RN-USR-04: el Administrador habilita o da de baja al profesional externo. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class VerificacionColegiadoTest extends PruebaIntegracion {

  private static final String DOMINIO = "@colegiado-prueba.com";
  private static final String CONTRASENA = "Puentes2026ok";
  private static final String MOTIVO = "El colegiado no coincide con el registro";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;

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

  private Usuario profesional(String correo) {
    Usuario cuenta = Cuentas.estudiante(correo);
    cuenta.setNombre("Ana Profesional");
    cuenta.setRol(Rol.PROFESIONAL_EXTERNO);
    cuenta.setEstado(EstadoUsuario.PENDIENTE);
    cuenta.setNumeroColegiado("CIG-" + Math.abs(correo.hashCode()));
    cuenta.setContrasenaHash(codificador.encode(CONTRASENA));
    return usuarios.saveAndFlush(cuenta);
  }

  private ResultActions verificar(UUID id) throws Exception {
    return mvc.perform(
        post("/api/v1/usuarios/" + id + "/colegiado/verificacion")
            .with(como(Rol.ADMINISTRADOR, admin.getId())));
  }

  private ResultActions rechazar(UUID id, String motivo) throws Exception {
    return mvc.perform(
        post("/api/v1/usuarios/" + id + "/colegiado/rechazo")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"motivo\":\"%s\"}".formatted(motivo))
            .with(como(Rol.ADMINISTRADOR, admin.getId())));
  }

  private ResultActions login(String correo) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, CONTRASENA)));
  }

  @Test
  void aprobarActivaAlProfesionalYLePermiteIniciarSesion() throws Exception {
    Usuario profesional = profesional("ana" + DOMINIO);

    login(profesional.getCorreo())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/cuenta-pendiente")));

    verificar(profesional.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ACTIVO"))
        .andExpect(jsonPath("$.colegiado_verificado_en").isNotEmpty())
        .andExpect(jsonPath("$.contrasena_hash").doesNotExist());

    Usuario leido = usuarios.findById(profesional.getId()).orElseThrow();
    assertThat(leido.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
    assertThat(leido.getColegiadoVerificadoEn()).isNotNull();
    assertThat(leido.getColegiadoVerificadoPor().getId()).isEqualTo(admin.getId());

    login(profesional.getCorreo())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usuario.rol").value("PROFESIONAL_EXTERNO"));
  }

  @Test
  void rechazarDaDeBajaConMotivoYElLoginRespondeCuentaInactiva() throws Exception {
    Usuario profesional = profesional("rechazada" + DOMINIO);

    rechazar(profesional.getId(), MOTIVO)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("INACTIVO"))
        .andExpect(jsonPath("$.motivo_baja").value(MOTIVO));

    Usuario leido = usuarios.findById(profesional.getId()).orElseThrow();
    assertThat(leido.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
    assertThat(leido.getDesactivadoPor().getId()).isEqualTo(admin.getId());
    assertThat(leido.getColegiadoVerificadoEn()).isNull();

    login(profesional.getCorreo())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/cuenta-inactiva")));

    Map<String, Object> auditoria =
        jdbc.queryForMap(
            "SELECT accion, motivo, valor_nuevo::text AS nuevo FROM auditoria"
                + " WHERE accion = 'DAR_DE_BAJA' AND entidad_id = ?",
            profesional.getId());
    assertThat(auditoria.get("motivo")).isEqualTo(MOTIVO);
    assertThat(auditoria.get("nuevo").toString()).contains("INACTIVO").doesNotContain(CONTRASENA);
  }

  @Test
  void noVerificaAQuienNoEsUnProfesionalPendiente() throws Exception {
    Usuario estudiante = usuarios.saveAndFlush(Cuentas.estudiante("alumno" + DOMINIO));
    Usuario catedratico = Cuentas.estudiante("docente" + DOMINIO);
    catedratico.setRol(Rol.CATEDRATICO);
    usuarios.saveAndFlush(catedratico);
    Usuario yaVerificado = profesional("ya" + DOMINIO);
    verificar(yaVerificado.getId()).andExpect(status().isOk());

    verificar(estudiante.getId())
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/colegiado-no-verificable")));
    verificar(catedratico.getId())
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/colegiado-no-verificable")));
    verificar(yaVerificado.getId())
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/colegiado-no-verificable")));
    verificar(UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/usuario-no-encontrado")));
  }

  @Test
  void auditaLaAprobacionSinCorreoCompletoNiHash() throws Exception {
    Usuario profesional = profesional("auditada" + DOMINIO);
    verificar(profesional.getId()).andExpect(status().isOk());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT accion, usuario_id, valor_anterior::text AS anterior, valor_nuevo::text AS nuevo"
                + " FROM auditoria WHERE accion = 'VERIFICAR_COLEGIADO' AND entidad_id = ?",
            profesional.getId());

    assertThat(filas).hasSize(1);
    assertThat(filas.getFirst().get("usuario_id")).isEqualTo(admin.getId());
    String valores = filas.getFirst().get("anterior") + " " + filas.getFirst().get("nuevo");
    assertThat(valores)
        .contains("PENDIENTE", "ACTIVO")
        .doesNotContain(CONTRASENA)
        .doesNotContain(profesional.getContrasenaHash())
        .doesNotContain("auditada" + DOMINIO);
  }
}
