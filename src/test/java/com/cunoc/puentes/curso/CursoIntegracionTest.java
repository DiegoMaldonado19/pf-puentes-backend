package com.cunoc.puentes.curso;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.curso.dto.GuardarCursoDTO;
import com.cunoc.puentes.curso.dto.InscribirEstudianteDTO;
import com.cunoc.puentes.usuario.Rol;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Sql("/curso/datos.sql")
@Sql(scripts = "/curso/limpiar.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CursoIntegracionTest extends PruebaIntegracion {
  static final UUID DOCENTE = id(101);
  static final UUID AJENO = id(102);
  static final UUID ESTUDIANTE = id(103);
  static final UUID PENDIENTE = id(105);
  static final UUID CURSO = id(301);
  static final UUID INSCRIPCION = id(401);
  static final UUID PUENTE = id(201);
  static final UUID ASIGNACION = id(501);
  static final String BASE = "/api/v1/cursos";
  static final String DATOS =
      "{\"nombre\":\"  Curso nuevo  \",\"anio\":2026,\"semestre\":2,\"vigente\":true}";

  @Autowired MockMvc mvc;
  @Autowired CursoService servicio;
  @Autowired JdbcClient jdbc;
  @MockitoBean ActivacionEstudiante activacion;

  @Value("${sgp.jwt.secreto}")
  String secretoJwt;

  @Test
  void creaEditaYAuditaSinExponerDatosPersonales() throws Exception {
    String cuerpo =
        mvc.perform(
                post(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(DATOS)
                    .with(csrf())
                    .with(como(Rol.CATEDRATICO, DOCENTE)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("Curso nuevo"))
            .andExpect(jsonPath("$.catedratico_id").value(DOCENTE.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain("contrasena", "correo");
    assertThat(
            jdbc.sql(
                    "select usuario_id from auditoria where entidad = 'curso' and valor_nuevo->>'nombre' = 'Curso nuevo'")
                .query(UUID.class)
                .single())
        .isEqualTo(DOCENTE);
    var curso = servicio.crear(DOCENTE, new GuardarCursoDTO("Editar", 2026, 2, true));
    assertThat(curso.id().version()).isEqualTo(7);
    servicio.actualizar(DOCENTE, curso.id(), new GuardarCursoDTO("Editado", 2026, 2, false));
    var auditoria =
        jdbc.sql(
                "select valor_anterior::text, valor_nuevo::text from auditoria where entidad_id = :id and accion = 'MODIFICAR'")
            .param("id", curso.id())
            .query((rs, n) -> rs.getString(1) + rs.getString(2))
            .single();
    assertThat(auditoria)
        .contains("true", "false", "Editar", "Editado")
        .doesNotContain("correo", "hash", "contrasena");
    assertThat(
            jdbc.sql("select count(*) from auditoria where entidad_id = :id")
                .param("id", curso.id())
                .query(Long.class)
                .single())
        .isEqualTo(2);
    assertThat(
            jdbc.sql(
                    "select usuario_id is null from auditoria where entidad_id = :id and accion = 'MODIFICAR'")
                .param("id", curso.id())
                .query(Boolean.class)
                .single())
        .isTrue();
    assertThat(jdbc.sql("select id from auditoria limit 1").query(UUID.class).single().version())
        .isEqualTo(7);
  }

  @Test
  void catedraticoNoConsultaNiModificaCursosAjenosNiInscripcionesDeOtroCurso() throws Exception {
    mvc.perform(get(BASE + "/" + id(302)).with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch(BASE + "/" + id(302))
                .contentType(MediaType.APPLICATION_JSON)
                .content(DATOS)
                .with(csrf())
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get(BASE + "/" + CURSO + "/inscripciones/" + id(402) + "/asignaciones")
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isNotFound());
    assertThat(servicio.listar(DOCENTE, PageRequest.of(0, 200)).getSize()).isEqualTo(100);
    assertThat(servicio.listar(DOCENTE, PageRequest.of(0, 20)).getContent())
        .allMatch(c -> c.catedraticoId().equals(DOCENTE));
    assertThat(jdbc.sql("select count(*) from auditoria").query(Long.class).single()).isZero();
  }

  @Test
  void inscribeActivoYRechazaDuplicadosSinDuplicarFilas() throws Exception {
    var creado = servicio.inscribir(DOCENTE, CURSO, new InscribirEstudianteDTO(id(104)));
    assertThat(creado.cursoId()).isEqualTo(CURSO);
    mvc.perform(
            post(BASE + "/" + CURSO + "/inscripciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudiante_id\":\"" + id(104) + "\"}")
                .with(csrf())
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://sgp.cunoc.usac.edu.gt/errores/inscripcion-duplicada"));
    assertThat(
            servicio.listarInscripciones(DOCENTE, CURSO, PageRequest.of(0, 20)).getTotalElements())
        .isEqualTo(2);
  }

  @Test
  void asignaPuenteYRechazaDuplicadosInactivosEInscripcionAjena() throws Exception {
    var inscrito = servicio.inscribir(DOCENTE, CURSO, new InscribirEstudianteDTO(id(104)));
    var datos = new com.cunoc.puentes.curso.dto.AsignarPuenteDTO(PUENTE);
    var asignada = servicio.asignar(DOCENTE, CURSO, inscrito.id(), datos);
    assertThat(asignada.estudianteId()).isEqualTo(id(104));
    String ruta = BASE + "/" + CURSO + "/inscripciones/" + inscrito.id() + "/asignaciones";
    mvc.perform(
            post(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puente_id\":\"" + PUENTE + "\"}")
                .with(csrf())
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isConflict());
    assertThatThrownBy(
            () ->
                servicio.asignar(
                    DOCENTE,
                    CURSO,
                    inscrito.id(),
                    new com.cunoc.puentes.curso.dto.AsignarPuenteDTO(id(202))))
        .isInstanceOf(NegocioException.class)
        .hasMessageContaining("422");
    assertThatThrownBy(() -> servicio.asignar(DOCENTE, CURSO, id(402), datos))
        .isInstanceOf(NegocioException.class);
    assertThat(
            servicio
                .listarAsignaciones(DOCENTE, CURSO, inscrito.id(), PageRequest.of(0, 20))
                .getTotalElements())
        .isEqualTo(1);
  }

  @Test
  void contrato6SoloDevuelveAsignacionesPropiasVigentesYDePuentesActivos() throws Exception {
    mvc.perform(
            get("/api/v1/mis-asignaciones")
                .param("estudiante_id", id(104).toString())
                .with(user(ESTUDIANTE.toString()).roles("ESTUDIANTE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].estudiante_id").value(ESTUDIANTE.toString()))
        .andExpect(jsonPath("$.content[0].curso_id").value(CURSO.toString()));
    assertThat(servicio.exigirAsignacionVigente(ESTUDIANTE, ASIGNACION).puenteId())
        .isEqualTo(PUENTE);
    assertThatThrownBy(() -> servicio.exigirAsignacionVigente(ESTUDIANTE, id(502)))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.exigirAsignacionVigente(ESTUDIANTE, id(503)))
        .isInstanceOf(NegocioException.class);
    servicio.actualizar(DOCENTE, CURSO, new GuardarCursoDTO("Cerrado", 2026, 2, false));
    assertThat(servicio.consultarAsignacionesVigentes(ESTUDIANTE, PageRequest.of(0, 20))).isEmpty();
    assertThatThrownBy(() -> servicio.exigirAsignacionVigente(ESTUDIANTE, ASIGNACION))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(
            () -> servicio.inscribir(DOCENTE, CURSO, new InscribirEstudianteDTO(id(104))))
        .isInstanceOf(NegocioException.class);
    assertThat(servicio.listarAsignaciones(DOCENTE, CURSO, INSCRIPCION, PageRequest.of(0, 20)))
        .hasSize(1);
  }

  @Test
  void contrato6ExcluyePuentesInactivosYUsuariosDeshabilitados() {
    jdbc.sql("update puente set activo = false, motivo_baja = 'Demolido' where id = :id")
        .param("id", PUENTE)
        .update();
    assertThat(servicio.consultarAsignacionesVigentes(ESTUDIANTE, PageRequest.of(0, 20))).isEmpty();
    assertThatThrownBy(() -> servicio.exigirAsignacionVigente(ESTUDIANTE, ASIGNACION))
        .isInstanceOf(NegocioException.class);
    assertThatThrownBy(() -> servicio.consultarAsignacionesVigentes(id(106), PageRequest.of(0, 20)))
        .isInstanceOf(NegocioException.class);
  }

  @Test
  void activacionDeP2ParticipaEnLaTransaccionYUnaFallaNoDejaInscripcion() {
    doAnswer(
            invocacion -> {
              jdbc.sql("update usuario set estado = 'ACTIVO' where id = :id")
                  .param("id", PENDIENTE)
                  .update();
              throw new IllegalStateException("Falla de P2");
            })
        .when(activacion)
        .activar(any(), any());
    assertThatThrownBy(
            () -> servicio.inscribir(DOCENTE, CURSO, new InscribirEstudianteDTO(PENDIENTE)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.sql("select estado from usuario where id = :id")
                .param("id", PENDIENTE)
                .query(String.class)
                .single())
        .isEqualTo("PENDIENTE");
    assertThat(
            jdbc.sql("select count(*) from inscripcion where estudiante_id = :id")
                .param("id", PENDIENTE)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(jdbc.sql("select count(*) from auditoria").query(Long.class).single()).isZero();
  }

  @Test
  void activaMediantePuertoP2YGuardaInscripcionAuditada() {
    doAnswer(
            invocacion -> {
              jdbc.sql("update usuario set estado = 'ACTIVO' where id = :id")
                  .param("id", PENDIENTE)
                  .update();
              return null;
            })
        .when(activacion)
        .activar(PENDIENTE, DOCENTE);
    assertThat(
            servicio
                .inscribir(DOCENTE, CURSO, new InscribirEstudianteDTO(PENDIENTE))
                .estudianteId())
        .isEqualTo(PENDIENTE);
    assertThat(
            jdbc.sql(
                    "select count(*) from auditoria where accion = 'CREAR' and entidad = 'inscripcion'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void validaEstudiantesYSelectoresSinCredenciales() throws Exception {
    for (int numero : new int[] {106, 107, 102}) {
      assertThatThrownBy(
              () -> servicio.inscribir(DOCENTE, CURSO, new InscribirEstudianteDTO(id(numero))))
          .isInstanceOf(NegocioException.class);
    }
    mvc.perform(
            get(BASE + "/estudiantes-elegibles")
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(19))
        .andExpect(jsonPath("$.content[0].correo").doesNotExist());
    mvc.perform(
            get(BASE + "/puentes-disponibles").with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1));
  }

  @Test
  void dtoInvalidoResponde400YNoEscribe() throws Exception {
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\" \",\"anio\":2019,\"semestre\":3}")
                .with(csrf())
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores").isArray());
    assertThat(jdbc.sql("select count(*) from curso").query(Long.class).single()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "PROFESIONAL_EXTERNO", "ADMINISTRADOR"})
  void matrizDeRolesNiegaCadaEndpointDeGestion(String rol) throws Exception {
    for (MockHttpServletRequestBuilder peticion : gestion()) {
      mvc.perform(peticion.with(csrf()).with(user(ESTUDIANTE.toString()).roles(rol)))
          .andExpect(status().isForbidden());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"CATEDRATICO", "PROFESIONAL_EXTERNO", "ADMINISTRADOR"})
  void soloEstudianteConsultaMisAsignaciones(String rol) throws Exception {
    mvc.perform(get("/api/v1/mis-asignaciones").with(user(DOCENTE.toString()).roles(rol)))
        .andExpect(status().isForbidden());
  }

  @Test
  void cadaEndpointExigeSesion() throws Exception {
    for (MockHttpServletRequestBuilder peticion : gestion()) {
      mvc.perform(peticion.with(csrf()).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(get("/api/v1/mis-asignaciones").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void endpointsCreanInscripcionYAsignacionConElRolCorrecto() throws Exception {
    mvc.perform(
            post(BASE + "/" + CURSO + "/inscripciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudiante_id\":\"" + id(104) + "\"}")
                .with(csrf())
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estudiante_id").value(id(104).toString()));
    UUID inscrito =
        jdbc.sql(
                "select id from inscripcion where curso_id = :curso and estudiante_id = :estudiante")
            .param("curso", CURSO)
            .param("estudiante", id(104))
            .query(UUID.class)
            .single();
    mvc.perform(
            post(BASE + "/" + CURSO + "/inscripciones/" + inscrito + "/asignaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puente_id\":\"" + PUENTE + "\"}")
                .with(csrf())
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.curso_id").value(CURSO.toString()))
        .andExpect(jsonPath("$.inscripcion_id").value(inscrito.toString()));
    mvc.perform(
            patch(BASE + "/" + CURSO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(DATOS)
                .with(csrf())
                .with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isOk());
  }

  @Test
  void dosInscripcionesConcurrentesProducenUnaFilaYUn409() throws Exception {
    CountDownLatch inicio = new CountDownLatch(1);
    Callable<Integer> operacion =
        () -> {
          inicio.await();
          try {
            servicio.inscribir(DOCENTE, CURSO, new InscribirEstudianteDTO(id(104)));
            return 201;
          } catch (NegocioException ex) {
            return ex.getStatusCode().value();
          }
        };
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var primera = ejecutor.submit(operacion);
      var segunda = ejecutor.submit(operacion);
      inicio.countDown();
      assertThat(
              java.util.List.of(
                  primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(
            jdbc.sql(
                    "select count(*) from inscripcion where curso_id = :curso and estudiante_id = :estudiante")
                .param("curso", CURSO)
                .param("estudiante", id(104))
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from auditoria where accion = 'CREAR' and entidad = 'inscripcion'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void openApiPublicaDtoYRespuestasSemanticasDeCursos() throws Exception {
    mvc.perform(get("/api/docs").with(user(DOCENTE.toString()).roles("CATEDRATICO")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/cursos'].post.responses['201']").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/cursos'].post.requestBody.content['application/json'].schema")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/cursos/{cursoId}/inscripciones'].post.responses['503']")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/cursos/{cursoId}/inscripciones/{inscripcionId}/asignaciones'].post.responses['409']")
                .exists())
        .andExpect(jsonPath("$.paths['/api/v1/mis-asignaciones'].get.responses['200']").exists())
        .andExpect(jsonPath("$.components.schemas.GuardarCursoDTO.required").isArray())
        .andExpect(jsonPath("$.components.schemas.AsignacionDTO.properties.curso_id").exists())
        .andExpect(
            jsonPath("$.components.schemas.AsignacionDTO.properties.inscripcion_id").exists());
  }

  @Test
  void filtrosJwtAutorizanCursosConRolDeBaseYRevocanAccesoTrasBaja() throws Exception {
    String token =
        Jwts.builder()
            .subject(DOCENTE.toString())
            .claim("rol", "ADMINISTRADOR")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(
                Keys.hmacShaKeyFor(secretoJwt.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
            .compact();
    mvc.perform(
            post(BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(DATOS))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.sql("select usuario_id from auditoria where entidad = 'curso'")
                .query(UUID.class)
                .single())
        .isEqualTo(DOCENTE);
    assertThat(
            jdbc.sql("select correlacion_id from auditoria where entidad = 'curso'")
                .query(UUID.class)
                .single())
        .isNotNull();
    jdbc.sql(
            "update usuario set estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'Baja de prueba' where id = :id")
        .param("id", DOCENTE)
        .update();
    mvc.perform(get(BASE).header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    mvc.perform(get(BASE).header("Authorization", "Bearer invalido"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void nombresYCodigosSeDevuelvenSinCredencialesYConContextoAcademico() throws Exception {
    mvc.perform(get(BASE + "/" + CURSO + "/inscripciones").with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].estudiante_nombre").value("Estudiante activo"))
        .andExpect(jsonPath("$.content[0].correo").doesNotExist());
    mvc.perform(get("/api/v1/mis-asignaciones").with(como(Rol.ESTUDIANTE, ESTUDIANTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].puente_codigo").value("GT-99-9901-0201"))
        .andExpect(jsonPath("$.content[0].puente_nombre").value("Puente activo"))
        .andExpect(jsonPath("$.content[0].curso_nombre").value("Puentes propio"));
    // La consulta histórica mantiene etiquetas incluso después de dar de baja al puente.
    jdbc.sql("update puente set activo = false, motivo_baja = 'Baja de prueba' where id = :id")
        .param("id", PUENTE)
        .update();
    assertThat(
            servicio
                .listarAsignaciones(DOCENTE, CURSO, INSCRIPCION, PageRequest.of(0, 20))
                .getContent()
                .getFirst()
                .puenteNombre())
        .isEqualTo("Puente activo");
    assertThat(servicio.consultarAsignacionesVigentes(ESTUDIANTE, PageRequest.of(0, 20))).isEmpty();
  }

  @Test
  void busquedaDeSelectoresEsPaginadaLiteralYRespetaElegibilidad() throws Exception {
    mvc.perform(
            get(BASE + "/estudiantes-elegibles")
                .param("q", "  ESTUDIANTE AJENO  ")
                .param("size", "1")
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id(104).toString()));
    mvc.perform(
            get(BASE + "/estudiantes-elegibles")
                .param("q", "inactivo")
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(jsonPath("$.total_elements").value(0));
    mvc.perform(
            get(BASE + "/puentes-disponibles")
                .param("q", "0201")
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(jsonPath("$.total_elements").value(1));
    for (String q : java.util.List.of("%", "_", "!", "' OR 1=1 --")) {
      mvc.perform(
              get(BASE + "/puentes-disponibles").param("q", q).with(como(Rol.CATEDRATICO, DOCENTE)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total_elements").value(0));
    }
    mvc.perform(
            get(BASE + "/puentes-disponibles")
                .param("q", "a".repeat(121))
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isBadRequest());
    assertThat(
            servicio
                .estudiantesElegibles(DOCENTE, PageRequest.of(1, 1), "estudiante")
                .getTotalElements())
        .isEqualTo(3);
  }

  @Test
  void falloDeAuditoriaGeneralRevierteLaCreacionDeCurso() {
    jdbc.sql(
            "alter table auditoria add constraint prueba_p7_fallo_check check (entidad <> 'curso') not valid")
        .update();
    try {
      assertThatThrownBy(
              () -> servicio.crear(DOCENTE, new GuardarCursoDTO("No persiste", 2026, 2, true)))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(
              jdbc.sql("select count(*) from curso where nombre = 'No persiste'")
                  .query(Long.class)
                  .single())
          .isZero();
      assertThat(jdbc.sql("select count(*) from auditoria").query(Long.class).single()).isZero();
    } finally {
      jdbc.sql("alter table auditoria drop constraint prueba_p7_fallo_check").update();
    }
  }

  @Test
  void catedraticoPuedeConsultarCadaListadoPropio() throws Exception {
    for (MockHttpServletRequestBuilder peticion :
        new MockHttpServletRequestBuilder[] {
          get(BASE),
          get(BASE + "/" + CURSO),
          get(BASE + "/" + CURSO + "/inscripciones"),
          get(BASE + "/" + CURSO + "/inscripciones/" + INSCRIPCION + "/asignaciones")
        }) {
      mvc.perform(peticion.with(user(DOCENTE.toString()).roles("CATEDRATICO")))
          .andExpect(status().isOk());
    }
  }

  private static MockHttpServletRequestBuilder[] gestion() {
    String inscripciones = BASE + "/" + CURSO + "/inscripciones";
    String asignaciones = inscripciones + "/" + INSCRIPCION + "/asignaciones";
    return new MockHttpServletRequestBuilder[] {
      get(BASE),
      post(BASE).contentType(MediaType.APPLICATION_JSON).content(DATOS),
      get(BASE + "/" + CURSO),
      patch(BASE + "/" + CURSO).contentType(MediaType.APPLICATION_JSON).content(DATOS),
      get(inscripciones),
      post(inscripciones)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"estudiante_id\":\"" + ESTUDIANTE + "\"}"),
      get(asignaciones),
      post(asignaciones)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"puente_id\":\"" + PUENTE + "\"}"),
      get(BASE + "/estudiantes-elegibles"),
      get(BASE + "/puentes-disponibles")
    };
  }

  private static UUID id(int numero) {
    return UUID.fromString("0192f5a0-0000-7000-8000-" + String.format("%012d", numero));
  }
}
