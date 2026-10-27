package com.cunoc.puentes.revision;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/curso/datos.sql", "/revision/datos.sql"})
@Sql(
    scripts = {"/revision/limpiar.sql", "/curso/limpiar.sql"},
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SeguimientoRevisionIntegracionTest extends PruebaIntegracion {
  private static final UUID DOCENTE = UUID.fromString("0192f5a0-0000-7000-8000-000000000101");
  private static final UUID AUTOR = UUID.fromString("0192f5a0-0000-7000-8000-000000000103");
  private static final UUID AJENO = UUID.fromString("0192f5a0-0000-7000-8000-000000000102");
  private static final UUID INSPECCION = UUID.fromString("0199b700-0000-7000-8000-000000000011");
  private static final UUID OBSERVACION = UUID.fromString("0199b700-0000-7000-8000-000000000090");
  @Autowired RevisionService apertura;
  @Autowired DecisionRevisionService decisiones;
  @Autowired JdbcClient jdbc;
  @Autowired MockMvc mvc;
  private UUID revision;

  @BeforeEach
  void cicloConObservacion() {
    revision = apertura.abrir(DOCENTE, INSPECCION).id();
    jdbc.sql(
            "insert into observacion_anclada (id, revision_id, elemento_ref, texto) values (:id, :revision, 'estribo.material', 'Confirmar material')")
        .param("id", OBSERVACION)
        .param("revision", revision)
        .update();
  }

  @Test
  void pendientesSoloSePublicanTrasVeredictoYAutorResuelveIdempotentemente() throws Exception {
    mvc.perform(get("/api/v1/revisiones/mis-pendientes").with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    cambios();
    mvc.perform(get("/api/v1/revisiones/mis-pendientes").with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1));
    mvc.perform(
            patch("/api/v1/observaciones/" + OBSERVACION + "/resolucion")
                .with(como(Rol.ESTUDIANTE, AJENO)))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/observaciones/" + OBSERVACION + "/resolucion")
                .with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resuelta_en").isNotEmpty());
    var fecha =
        jdbc.sql("select resuelta_en from observacion_anclada where id = :id")
            .param("id", OBSERVACION)
            .query(java.time.Instant.class)
            .single();
    mvc.perform(
            patch("/api/v1/observaciones/" + OBSERVACION + "/resolucion")
                .with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.sql("select resuelta_en from observacion_anclada where id = :id")
                .param("id", OBSERVACION)
                .query(java.time.Instant.class)
                .single())
        .isEqualTo(fecha);
    mvc.perform(get("/api/v1/revisiones/mis-pendientes").with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(jsonPath("$.total_elements").value(0));
  }

  @Test
  void noResuelveNotasPrivadasNiInspeccionQueYaNoEsBorrador() throws Exception {
    mvc.perform(
            patch("/api/v1/observaciones/" + OBSERVACION + "/resolucion")
                .with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isNotFound());
    decisiones.emitir(
        DOCENTE, revision, new DecisionRevisionDTO(Veredicto.RECHAZADA, "Rechazo", null, null));
    mvc.perform(
            patch("/api/v1/observaciones/" + OBSERVACION + "/resolucion")
                .with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isConflict());
  }

  @Test
  void conservaDosCiclosYObservacionesPreviasSinFiltrarCalificacion() throws Exception {
    cambios();
    // Simula el reenvío de Inspecciones sin depender de su captura/fotos, probada por su módulo.
    jdbc.sql("update inspeccion set estado = 'ENVIADA', enviada_en = now() where id = :id")
        .param("id", INSPECCION)
        .update();
    UUID nueva = apertura.abrir(DOCENTE, INSPECCION).id();
    assertThat(nueva).isNotEqualTo(revision);
    mvc.perform(
            get("/api/v1/inspecciones/" + INSPECCION + "/revisiones")
                .with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(2))
        .andExpect(jsonPath("$.content[1].veredicto").value("CAMBIOS_SOLICITADOS"))
        .andExpect(jsonPath("$.content[1].observaciones[0].texto").value("Confirmar material"))
        .andExpect(jsonPath("$.content[0].calificacion").doesNotExist());
    mvc.perform(
            get("/api/v1/inspecciones/" + INSPECCION + "/revisiones")
                .with(como(Rol.CATEDRATICO, AJENO)))
        .andExpect(status().isNotFound());
  }

  @Test
  void calificacionPrivadaSoloAutorYRevisorSinFugaPorBitacora() throws Exception {
    mvc.perform(
            put("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":87.50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calificacion").value(87.5));
    mvc.perform(
            get("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calificacion").value(87.5));
    mvc.perform(
            get("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.CATEDRATICO, AJENO)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.ADMINISTRADOR, AJENO)))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.ESTUDIANTE, AUTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":100}"))
        .andExpect(status().isForbidden());
    var snapshots =
        jdbc.sql(
                "select coalesce(valor_anterior::text, '') || coalesce(valor_nuevo::text, '') from auditoria where entidad_id = :id")
            .param("id", revision)
            .query(String.class)
            .list();
    assertThat(snapshots)
        .allSatisfy(texto -> assertThat(texto).doesNotContain("calificacion", "87.5"));
    mvc.perform(
            put("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":101}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void historialSigueAccesibleSinPendientesYNoAdmiteUnRevisorDegradado() throws Exception {
    cambios();
    mvc.perform(
            patch("/api/v1/observaciones/" + OBSERVACION + "/resolucion")
                .with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/revisiones/mis-inspecciones").with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1));
    mvc.perform(
            get("/api/v1/revisiones/mis-inspecciones")
                .with(
                    como(Rol.ESTUDIANTE, UUID.fromString("0192f5a0-0000-7000-8000-000000000104"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    jdbc.sql("update usuario set rol = 'ESTUDIANTE' where id = :id").param("id", DOCENTE).update();
    mvc.perform(
            get("/api/v1/inspecciones/" + INSPECCION + "/revisiones")
                .with(como(Rol.ESTUDIANTE, DOCENTE)))
        .andExpect(status().isNotFound());
  }

  @Test
  void validaPrecisionYPermiteRetirarLaNotaSinDarEscrituraAlOtroDocente() throws Exception {
    mvc.perform(
            put("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":80.123}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.CATEDRATICO, AJENO))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":80}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":80}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/v1/revisiones/" + revision + "/calificacion")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":null}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.sql("select count(*) from revision where id = :id and calificacion is null")
                .param("id", revision)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  private void cambios() {
    decisiones.emitir(
        DOCENTE,
        revision,
        new DecisionRevisionDTO(Veredicto.CAMBIOS_SOLICITADOS, "Corregir", null, null));
  }
}
