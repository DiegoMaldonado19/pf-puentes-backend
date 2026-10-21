package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.curso.ActivacionEstudiante;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** US-P2-14: la activación real de P2 dentro de la inscripción de P7 (ADR 0018). */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/curso/datos.sql")
@Sql(scripts = "/curso/limpiar.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ActivacionDeEstudianteTest extends PruebaIntegracion {

  static final UUID DOCENTE = id(101);
  static final UUID ACTIVO = id(103);
  static final UUID PENDIENTE = id(105);
  static final UUID INACTIVO = id(106);
  static final UUID SIN_VERIFICAR = id(107);
  static final UUID CURSO = id(301);

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ActivacionEstudiante activacion;
  @Autowired PlatformTransactionManager gestor;

  @Test
  void inscribirAUnPendienteLoActivaYAuditaEnLaMismaOperacion() throws Exception {
    mvc.perform(
            post("/api/v1/cursos/" + CURSO + "/inscripciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudiante_id\":\"" + PENDIENTE + "\"}")
                .with(csrf())
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isCreated());

    assertThat(estado(PENDIENTE)).isEqualTo("ACTIVO");
    var cambio =
        jdbc.sql(
                """
                select usuario_id, correlacion_id, valor_anterior->>'estado',
                       valor_nuevo->>'estado', valor_nuevo::text
                from auditoria
                where accion = 'CAMBIAR_ESTADO' and entidad = 'usuario' and entidad_id = :id
                """)
            .param("id", PENDIENTE)
            .query(
                (rs, n) ->
                    new String[] {
                      rs.getString(1),
                      rs.getString(2),
                      rs.getString(3),
                      rs.getString(4),
                      rs.getString(5)
                    })
            .single();
    assertThat(cambio[0]).isEqualTo(DOCENTE.toString());
    assertThat(cambio[2]).isEqualTo("PENDIENTE");
    assertThat(cambio[3]).isEqualTo("ACTIVO");
    assertThat(cambio[4]).doesNotContain("contrasena", "hash", "pendiente@test.edu");
    String correlacionInscripcion =
        jdbc.sql(
                "select correlacion_id::text from auditoria"
                    + " where accion = 'CREAR' and entidad = 'inscripcion'")
            .query(String.class)
            .single();
    assertThat(cambio[1]).isEqualTo(correlacionInscripcion);
  }

  @Test
  void fueraDeUnaInscripcionNoActiva() {
    assertThatThrownBy(() -> activacion.activar(PENDIENTE, DOCENTE))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(estado(PENDIENTE)).isEqualTo("PENDIENTE");
  }

  @Test
  void unEstudianteYaActivoNoCambiaNiSeAudita() {
    enTransaccion(() -> activacion.activar(ACTIVO, DOCENTE));

    assertThat(estado(ACTIVO)).isEqualTo("ACTIVO");
    assertThat(auditoriasDe(ACTIVO)).isZero();
  }

  @Test
  void rechazaLasActivacionesQueNoCumplenLasReglas() {
    assertRechazo(PENDIENTE, ACTIVO, HttpStatus.FORBIDDEN, "cuenta-no-habilitada");
    assertRechazo(PENDIENTE, UUID.randomUUID(), HttpStatus.FORBIDDEN, "cuenta-no-habilitada");
    assertRechazo(UUID.randomUUID(), DOCENTE, HttpStatus.NOT_FOUND, "usuario-no-encontrado");
    assertRechazo(id(102), DOCENTE, HttpStatus.NOT_FOUND, "usuario-no-encontrado");
    assertRechazo(INACTIVO, DOCENTE, HttpStatus.CONFLICT, "cuenta-inactiva");
    assertRechazo(SIN_VERIFICAR, DOCENTE, HttpStatus.UNPROCESSABLE_CONTENT, "correo-no-verificado");

    assertThat(estado(PENDIENTE)).isEqualTo("PENDIENTE");
    assertThat(estado(SIN_VERIFICAR)).isEqualTo("PENDIENTE");
    assertThat(auditoriasDe(PENDIENTE) + auditoriasDe(INACTIVO) + auditoriasDe(SIN_VERIFICAR))
        .isZero();
  }

  private void assertRechazo(UUID estudiante, UUID catedratico, HttpStatus estado, String tipo) {
    assertThatThrownBy(() -> enTransaccion(() -> activacion.activar(estudiante, catedratico)))
        .isInstanceOfSatisfying(
            NegocioException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(estado);
              assertThat(e.getBody().getType().toString()).endsWith("/" + tipo);
            });
  }

  private void enTransaccion(Runnable accion) {
    new TransactionTemplate(gestor).executeWithoutResult(transaccion -> accion.run());
  }

  private String estado(UUID id) {
    return jdbc.sql("select estado from usuario where id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private long auditoriasDe(UUID id) {
    return jdbc.sql("select count(*) from auditoria where entidad_id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private static UUID id(int sufijo) {
    return UUID.fromString("0192f5a0-0000-7000-8000-%012d".formatted(sufijo));
  }
}
