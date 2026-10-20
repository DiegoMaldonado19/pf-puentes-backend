package com.cunoc.puentes.curso;

import com.cunoc.puentes.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P7 contra login/renovación reales de P2, sin usuarios autenticados ni activación simulados.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Sql("/curso/datos.sql")
@Sql(scripts = "/curso/limpiar.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CursoSesionIntegracionTest extends PruebaIntegracion {
    private static final String CLAVE = "Puentes2026soloPruebas";
    private static final String DOCENTE = "0192f5a0-0000-7000-8000-000000000101";
    private static final String ESTUDIANTE = "0192f5a0-0000-7000-8000-000000000104";
    private static final String CURSO_AJENO = "0192f5a0-0000-7000-8000-000000000302";
    private static final String CURSO_PROPIO_DOCENTE1 = "0192f5a0-0000-7000-8000-000000000301";
    private static final String PUENTE = "0192f5a0-0000-7000-8000-000000000201";
    private static final String PENDIENTE = "0192f5a0-0000-7000-8000-000000000105";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    PasswordEncoder codificador;

    @Value("${sgp.jwt.secreto}")
    String secreto;

    @BeforeEach
    void contrasenasSoloEnFixtures() {
        jdbc.sql("update usuario set contrasena_hash = :hash where correo in (:correos)")
                .param("hash", codificador.encode(CLAVE))
                .param(
                        "correos",
                        java.util.List.of("docente1@test.edu", "alumno2@test.edu", "pendiente@test.edu"))
                .update();
    }

    @Test
    void loginCursoInscripcionAsignacionYConsultaPropiaConRolesReales() throws Exception {
        var docente = login("docente1@test.edu");
        var creado =
                mvc.perform(
                                post("/api/v1/cursos")
                                        .header(HttpHeaders.AUTHORIZATION, docente.bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                        {"nombre":"Curso con sesión real","anio":2026,"semestre":2,"vigente":true}
                                                        """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.catedratico_id").value(DOCENTE))
                        .andReturn();
        String curso = JsonPath.read(creado.getResponse().getContentAsString(), "$.id");
        var inscrito =
                mvc.perform(
                                post("/api/v1/cursos/" + curso + "/inscripciones")
                                        .header(HttpHeaders.AUTHORIZATION, docente.bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"estudiante_id\":\"" + ESTUDIANTE + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String inscripcion = JsonPath.read(inscrito.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/cursos/" + curso + "/inscripciones/" + inscripcion + "/asignaciones")
                                .header(HttpHeaders.AUTHORIZATION, docente.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"puente_id\":\"" + PUENTE + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.puente_codigo").value("GT-99-9901-0201"));
        var estudiante = login("alumno2@test.edu");
        mvc.perform(
                        get("/api/v1/mis-asignaciones").header(HttpHeaders.AUTHORIZATION, estudiante.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.content[0].estudiante_id").value(ESTUDIANTE));

        mvc.perform(get("/api/v1/cursos").header(HttpHeaders.AUTHORIZATION, estudiante.bearer()))
                .andExpect(status().isForbidden());

        mvc.perform(
                        get("/api/v1/cursos/" + CURSO_AJENO)
                                .header(HttpHeaders.AUTHORIZATION, docente.bearer()))
                .andExpect(status().isNotFound());

        assertThat(
                jdbc.sql(
                                "select count(*) from auditoria where usuario_id = cast(:id as uuid) and entidad_id in (cast(:curso as uuid), cast(:inscripcion as uuid))")
                        .param("id", DOCENTE)
                        .param("curso", curso)
                        .param("inscripcion", inscripcion)
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void accesoVencidoRenovacionYCierreRespetanElContratoDeP2() throws Exception {
        var sesion = login("docente1@test.edu");
        String vencido =
                Jwts.builder()
                        .subject(DOCENTE)
                        .issuedAt(Date.from(Instant.now().minusSeconds(1800)))
                        .expiration(Date.from(Instant.now().minusSeconds(900)))
                        .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                        .compact();
        mvc.perform(get("/api/v1/cursos").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
        var renovada =
                mvc.perform(post("/api/v1/auth/renovar").cookie(sesion.cookie()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.usuario.rol").value("CATEDRATICO"))
                        .andExpect(jsonPath("$.refresh_token").doesNotExist())
                        .andReturn();
        String token = JsonPath.read(renovada.getResponse().getContentAsString(), "$.access_token");
        mvc.perform(get("/api/v1/cursos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/cerrar-sesion").cookie(sesion.cookie()))
                .andExpect(status().isNoContent())
                .andExpect(
                        header()
                                .string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/renovar").cookie(sesion.cookie()))
                .andExpect(status().isUnauthorized());
        // Cerrar revoca la renovación; no invalida un JWT de acceso ya emitido (ADR 0017).
        mvc.perform(get("/api/v1/cursos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/cursos")).andExpect(status().isUnauthorized());
    }

    @Test
    void bajaImpideAccesoYCortaRenovacionParaCursos() throws Exception {
        var sesion = login("docente1@test.edu");
        jdbc.sql(
                        "update usuario set estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'Prueba P7' where id = cast(:id as uuid)")
                .param("id", DOCENTE)
                .update();
        mvc.perform(get("/api/v1/cursos").header(HttpHeaders.AUTHORIZATION, sesion.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/renovar").cookie(sesion.cookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendienteVerificadoAunNecesitaActivacionProductivaDeP2() throws Exception {
        var docente = login("docente1@test.edu");
        mvc.perform(
                        post("/api/v1/cursos/" + CURSO_PROPIO_DOCENTE1 + "/inscripciones")
                                .header(HttpHeaders.AUTHORIZATION, docente.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estudiante_id\":\"" + PENDIENTE + "\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(
                jdbc.sql("select count(*) from inscripcion where estudiante_id = cast(:id as uuid)")
                        .param("id", PENDIENTE)
                        .query(Long.class)
                        .single())
                .isZero();
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"correo\":\"pendiente@test.edu\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isForbidden());
    }

    private Sesion login(String correo) throws Exception {
        var resultado =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        String token = JsonPath.read(resultado.getResponse().getContentAsString(), "$.access_token");
        String cookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
        return new Sesion(token, cookie.substring("refresh_token=".length(), cookie.indexOf(';')));
    }

    private record Sesion(String acceso, String renovacion) {
        String bearer() {
            return "Bearer " + acceso;
        }

        Cookie cookie() {
            return new Cookie("refresh_token", renovacion);
        }

        @Override
        public String toString() {
            return "Sesion[credenciales omitidas]";
        }
    }
}
