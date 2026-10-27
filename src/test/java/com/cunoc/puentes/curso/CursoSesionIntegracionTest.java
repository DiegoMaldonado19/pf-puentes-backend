package com.cunoc.puentes.curso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica cursos con autenticación, activación y cookies reales, sin simular servicios. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Sql("/curso/datos.sql")
@Sql(scripts = "/curso/limpiar.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CursoSesionIntegracionTest extends PruebaIntegracion {
  private static final String CLAVE = "Cursos2026soloPruebas";
  private static final String CURSO = "0192f5a0-0000-7000-8000-000000000301";
  private static final String PENDIENTE = "0192f5a0-0000-7000-8000-000000000105";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder codificador;

  @BeforeEach
  void prepararContrasenasDeFixtures() {
    jdbc.sql("update usuario set contrasena_hash = :hash where correo in (:correos)")
        .param("hash", codificador.encode(CLAVE))
        .param(
            "correos",
            java.util.List.of("docente1@test.edu", "alumno2@test.edu", "pendiente@test.edu"))
        .update();
  }

  @Test
  void sesionAutorizaPorRolYPropiedad() throws Exception {
    mvc.perform(get("/api/v1/cursos")).andExpect(status().isUnauthorized());
    var docente = login("docente1@test.edu");
    mvc.perform(get("/api/v1/cursos/" + CURSO).header(HttpHeaders.AUTHORIZATION, docente.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(CURSO));
    mvc.perform(
            get("/api/v1/cursos/0192f5a0-0000-7000-8000-000000000302")
                .header(HttpHeaders.AUTHORIZATION, docente.bearer()))
        .andExpect(status().isNotFound());
    var estudiante = login("alumno2@test.edu");
    mvc.perform(get("/api/v1/cursos").header(HttpHeaders.AUTHORIZATION, estudiante.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/mis-asignaciones").header(HttpHeaders.AUTHORIZATION, estudiante.bearer()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.content[*].estudiante_id",
                org.hamcrest.Matchers.everyItem(
                    org.hamcrest.Matchers.is("0192f5a0-0000-7000-8000-000000000104"))));
  }

  @Test
  void inscribirActivaAlEstudianteVerificadoYPermiteLogin() throws Exception {
    var docente = login("docente1@test.edu");
    mvc.perform(
            post("/api/v1/cursos/" + CURSO + "/inscripciones")
                .header(HttpHeaders.AUTHORIZATION, docente.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudiante_id\":\"" + PENDIENTE + "\"}"))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.sql("select estado from usuario where id = cast(:id as uuid)")
                .param("id", PENDIENTE)
                .query(String.class)
                .single())
        .isEqualTo("ACTIVO");
    var estudiante = login("pendiente@test.edu");
    mvc.perform(
            get("/api/v1/mis-asignaciones").header(HttpHeaders.AUTHORIZATION, estudiante.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void renuevaLaSesionYElCierreRevocaSuCookie() throws Exception {
    var sesion = login("docente1@test.edu");
    var renovada =
        mvc.perform(post("/api/v1/auth/renovar").cookie(sesion.cookie()))
            .andExpect(status().isOk())
            .andReturn();
    String token = JsonPath.read(renovada.getResponse().getContentAsString(), "$.access_token");
    mvc.perform(get("/api/v1/cursos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/cerrar-sesion").cookie(sesion.cookie()))
        .andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/renovar").cookie(sesion.cookie()))
        .andExpect(status().isUnauthorized());
  }

  private Sesion login(String correo) throws Exception {
    var respuesta =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refresh_token").doesNotExist())
            .andReturn()
            .getResponse();
    String token = JsonPath.read(respuesta.getContentAsString(), "$.access_token");
    Cookie cookie = respuesta.getCookie("refresh_token");
    assertThat(cookie).isNotNull();
    return new Sesion("Bearer " + token, cookie);
  }

  private record Sesion(String bearer, Cookie cookie) {}
}
