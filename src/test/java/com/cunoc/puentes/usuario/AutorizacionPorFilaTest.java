package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
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

/**
 * DT-SEC-04: el id sale del JWT real, no del helper de MockMvc. Cubre cambio de contraseña ajeno,
 * baja propia y pérdida inmediata de acceso.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AutorizacionPorFilaTest extends PruebaIntegracion {

  private static final String DOMINIO = "@fila-auth-prueba.com";
  private static final String CONTRASENA = "Puentes2026ok";
  private static final String NUEVA = "Puentes2027ok";
  private static final String MOTIVO = "Baja de prueba de autorizacion";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UsuarioRepository usuarios;
  @Autowired PasswordEncoder codificador;

  private Usuario admin;
  private Usuario ana;
  private Usuario carlos;

  @BeforeEach
  void preparar() {
    borrarDatosDePrueba();
    admin = guardar(Cuentas.administrador("admin" + DOMINIO));
    ana = guardar(Cuentas.estudiante("ana" + DOMINIO));
    carlos = guardar(Cuentas.estudiante("carlos" + DOMINIO));
  }

  @AfterEach
  void limpiar() {
    borrarDatosDePrueba();
  }

  @Test
  void nadieCambiaLaContrasenaDeOtraCuentaAunquePaseSuId() throws Exception {
    String tokenAna = acceso(ana.getCorreo());

    mvc.perform(
            put("/api/v1/usuarios/me/contrasena")
                .param("id", carlos.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"contrasena_actual\":\"%s\",\"contrasena_nueva\":\"%s\"}"
                        .formatted(CONTRASENA, NUEVA))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAna))
        .andExpect(status().isNoContent());

    mvc.perform(
            put("/api/v1/usuarios/" + carlos.getId() + "/contrasena")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"contrasena_actual\":\"%s\",\"contrasena_nueva\":\"%s\"}"
                        .formatted(CONTRASENA, NUEVA))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAna))
        .andExpect(status().isNotFound());

    login(ana.getCorreo(), NUEVA).andExpect(status().isOk());
    login(carlos.getCorreo(), CONTRASENA).andExpect(status().isOk());
    login(carlos.getCorreo(), NUEVA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/credenciales-invalidas")));
    assertThat(codificador.matches(CONTRASENA, hash(carlos.getId()))).isTrue();
  }

  @Test
  void unAdministradorNoSeDaDeBajaASiMismoConSuJwt() throws Exception {
    String token = acceso(admin.getCorreo());

    mvc.perform(
            post("/api/v1/usuarios/" + admin.getId() + "/baja")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"%s\"}".formatted(MOTIVO))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(endsWith("/baja-propia-no-permitida")));

    mvc.perform(get("/api/v1/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(admin.getId().toString()))
        .andExpect(jsonPath("$.estado").value("ACTIVO"));
    assertThat(usuarios.findById(admin.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoUsuario.ACTIVO);
  }

  @Test
  void unaCuentaDadaDeBajaPierdeElAccesoEnLaPeticionSiguiente() throws Exception {
    String tokenAna = acceso(ana.getCorreo());
    String tokenAdmin = acceso(admin.getCorreo());
    mvc.perform(get("/api/v1/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAna))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/v1/usuarios/" + ana.getId() + "/baja")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"%s\"}".formatted(MOTIVO))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("INACTIVO"));

    mvc.perform(get("/api/v1/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAna))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(endsWith("/no-autenticado")));
    login(ana.getCorreo(), CONTRASENA)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(endsWith("/cuenta-inactiva")));
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
    cuenta.setNombre(cuenta.getCorreo().startsWith("admin") ? "Admin Fila" : "Persona Fila");
    cuenta.setContrasenaHash(codificador.encode(CONTRASENA));
    return usuarios.saveAndFlush(cuenta);
  }

  private String acceso(String correo) throws Exception {
    MvcResult inicio = login(correo, CONTRASENA).andExpect(status().isOk()).andReturn();
    return JsonPath.read(inicio.getResponse().getContentAsString(), "$.access_token");
  }

  private ResultActions login(String correo, String contrasena) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
  }

  private String hash(UUID id) {
    return usuarios.findById(id).orElseThrow().getContrasenaHash();
  }
}
