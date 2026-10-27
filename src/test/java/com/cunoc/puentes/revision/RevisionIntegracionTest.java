package com.cunoc.puentes.revision;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/curso/datos.sql", "/revision/datos.sql"})
@Sql(
    scripts = {"/revision/limpiar.sql", "/curso/limpiar.sql"},
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RevisionIntegracionTest extends PruebaIntegracion {
  private static final UUID DOCENTE = UUID.fromString("0192f5a0-0000-7000-8000-000000000101");
  private static final UUID ENVIADA = id(11);
  @Autowired MockMvc mvc;
  @Autowired RevisionService servicio;
  @Autowired JdbcClient jdbc;

  @Test
  void bandejaSoloIncluyeEstudiantesPropiosYPaginaSinCalificacion() throws Exception {
    mvc.perform(
            get("/api/v1/revisiones/pendientes")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].inspeccion_id").value(ENVIADA.toString()))
        .andExpect(jsonPath("$.content[0].calificacion").doesNotExist());
    mvc.perform(
            get("/api/v1/revisiones/pendientes")
                .with(como(Rol.CATEDRATICO, DOCENTE))
                .param("estado", "BORRADOR"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void abrirCreaCicloTransicionYAuditoriaYRechazaDuplicado() throws Exception {
    mvc.perform(
            post("/api/v1/inspecciones/" + ENVIADA + "/revisiones")
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.revisor_id").value(DOCENTE.toString()));
    assertThat(
            jdbc.sql("select estado from inspeccion where id = :id")
                .param("id", ENVIADA)
                .query(String.class)
                .single())
        .isEqualTo("EN_REVISION");
    assertThat(
            jdbc.sql(
                    "select count(*) from transicion_inspeccion where inspeccion_id = :id and revision_id is not null")
                .param("id", ENVIADA)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    mvc.perform(
            post("/api/v1/inspecciones/" + ENVIADA + "/revisiones")
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isConflict());
    mvc.perform(
            get("/api/v1/revisiones/pendientes")
                .param("estado", "EN_REVISION")
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].revision_id").isNotEmpty());
    assertThat(
            jdbc.sql(
                    "select count(*) from auditoria where entidad = 'revision' and usuario_id = :id")
                .param("id", DOCENTE)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void deniegaAnonimosEstudiantesAjenasYPropias() throws Exception {
    mvc.perform(get("/api/v1/revisiones/pendientes")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/revisiones/pendientes")
                .with(
                    como(Rol.ESTUDIANTE, UUID.fromString("0192f5a0-0000-7000-8000-000000000103"))))
        .andExpect(status().isForbidden());
    for (UUID inspeccion : List.of(id(12), id(13), id(15), UUID.randomUUID())) {
      mvc.perform(
              post("/api/v1/inspecciones/" + inspeccion + "/revisiones")
                  .with(como(Rol.CATEDRATICO, DOCENTE)))
          .andExpect(status().isNotFound());
    }
    mvc.perform(
            post("/api/v1/inspecciones/" + id(14) + "/revisiones")
                .with(como(Rol.CATEDRATICO, DOCENTE)))
        .andExpect(status().isConflict());
  }

  @Test
  void fallaAuditoriaRevierteTambienCicloYTransicion() {
    jdbc.sql(
            "alter table auditoria add constraint prueba_revision_fallo check (entidad <> 'revision') not valid")
        .update();
    try {
      assertThatThrownBy(() -> servicio.abrir(DOCENTE, ENVIADA))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(
              jdbc.sql("select count(*) from revision where inspeccion_id = :id")
                  .param("id", ENVIADA)
                  .query(Long.class)
                  .single())
          .isZero();
      assertThat(
              jdbc.sql("select estado from inspeccion where id = :id")
                  .param("id", ENVIADA)
                  .query(String.class)
                  .single())
          .isEqualTo("ENVIADA");
    } finally {
      jdbc.sql("alter table auditoria drop constraint prueba_revision_fallo").update();
    }
  }

  @Test
  void aperturasConcurrentesSoloCreanUnCiclo() throws Exception {
    Callable<Boolean> abrir =
        () -> {
          try {
            servicio.abrir(DOCENTE, ENVIADA);
            return true;
          } catch (NegocioException e) {
            return false;
          }
        };
    try (var executor = Executors.newFixedThreadPool(2)) {
      var resultados = executor.invokeAll(List.of(abrir, abrir));
      int exitos = 0;
      for (var resultado : resultados) if (resultado.get()) exitos++;
      assertThat(exitos).isEqualTo(1);
    }
    assertThat(
            jdbc.sql("select count(*) from revision where inspeccion_id = :id")
                .param("id", ENVIADA)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  private static UUID id(int n) {
    return UUID.fromString("0199b700-0000-7000-8000-" + String.format("%012d", n));
  }
}
