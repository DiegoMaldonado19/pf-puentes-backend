package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/inspeccion/consulta.sql", "/inspeccion/borrador.sql"})
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionBorradorTest extends PruebaIntegracion {

  static final UUID E1 = InspeccionConsultaTest.E1;
  static final UUID E2 = InspeccionConsultaTest.E2;
  static final UUID C1 = InspeccionConsultaTest.C1;
  static final UUID BORRADOR = InspeccionConsultaTest.BORRADOR_E1;
  static final UUID BORRADOR_E2 = InspeccionConsultaTest.BORRADOR_E2;
  static final UUID ENVIADA = InspeccionConsultaTest.ENVIADA;
  static final UUID PUBLICADA = InspeccionConsultaTest.PUBLICADA_RECIENTE;
  static final UUID ELIMINADA = InspeccionConsultaTest.ELIMINADA;
  static final UUID CAMBIOS = InspeccionConsultaTest.CAMBIOS;
  static final String BASE = "/api/v1/inspecciones/";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired AvisoBorradoresAbandonadosService avisos;
  @Autowired AvisoBorradoresAbandonadosJob job;
  @Autowired LockProvider candados;
  @Autowired InspeccionService contrato;

  @Test
  void guardarAvanceParcialSubeVersionYActividadSinExigirObligatorios() throws Exception {
    long version = version(BORRADOR);
    Instant actividad =
        jdbc.sql("select ultima_actividad_en from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Instant.class)
            .single();
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    avance(
                        version,
                        """
                    "identificacion":{"tipologia":"losas","numero_tramos":3}
                    """))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos.identificacion.tipologia").value("losas"))
        .andExpect(jsonPath("$.version").value(version + 1));
    Instant despues =
        jdbc.sql("select ultima_actividad_en from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Instant.class)
            .single();
    assertThat(despues).isAfter(actividad);
  }

  @Test
  void datosInvalidosSinDanosInmutablesFechaYTamanoSeRechazan() throws Exception {
    long version = version(BORRADOR);
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    avance(
                        version,
                        """
                    "subestructura":{"estribo_entrada":{"calificacion":7}}
                    """))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.errores[0].campo").value("subestructura.estribo_entrada.calificacion"));
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"subestructura":{"estribo_entrada":{"calificacion":2}}},\
                    "version":%d,"grupos_sin_danos":["subestructura.estribo_entrada"]}
                    """
                        .formatted(version))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[0].campo").value("subestructura.estribo_entrada"));
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"identificacion":{"tipologia":"losas"}},\
                    "version":%d,"puente_id":"%s"}
                    """
                        .formatted(version, InspeccionConsultaTest.LAS_ROSAS))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[0].campo").value("puente_id"));
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{},"version":%d,"fecha_inspeccion":"2999-01-01"}
                    """
                        .formatted(version))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[0].campo").value("fecha_inspeccion"));
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"identificacion":{"tipologia":"%s"}},"version":%d}
                    """
                        .formatted("x".repeat(300_000), version))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isPayloadTooLarge());
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"datos":{"subestructura":{"estribo_entrada":{"calificacion":0,\
                    "fisuras":0,"humedad":0,"desconchado":0,"asentamiento":0,"socavacion":0}}},\
                    "version":%d,"grupos_sin_danos":["subestructura.estribo_entrada"]}
                    """
                        .formatted(version))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos.subestructura.estribo_entrada.calificacion").value(0));
  }

  @Test
  void ajenoCatedraticoYNoBorradorNoEditan() throws Exception {
    String cuerpo =
        avance(
            version(BORRADOR),
            """
        "identificacion":{"tipologia":"losas"}
        """);
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E2)))
        .andExpect(status().isNotFound());
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)
                .with(csrf())
                .with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource({"ENVIADA", "PUBLICADA", "RECHAZADA"})
  void estadosCerradosNoSeEditan(String estado) throws Exception {
    UUID id =
        switch (estado) {
          case "ENVIADA" -> ENVIADA;
          case "PUBLICADA" -> PUBLICADA;
          default -> InspeccionConsultaTest.RECHAZADA;
        };
    mvc.perform(
            put(BASE + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    avance(
                        0,
                        """
                    "identificacion":{"tipologia":"losas"}
                    """))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("inspeccion-no-editable")));
  }

  @Test
  void unaDevueltaABorradorVuelveASerEditable() throws Exception {
    jdbc.sql("update inspeccion set estado = 'BORRADOR' where id = :id")
        .param("id", CAMBIOS)
        .update();
    mvc.perform(
            put(BASE + CAMBIOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    avance(
                        version(CAMBIOS),
                        """
                    "identificacion":{"tipologia":"losas"}
                    """))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos.identificacion.tipologia").value("losas"));
  }

  @Test
  void idempotenciaNoDuplicaYLaVersionViejaEnLineaEs409() throws Exception {
    long version = version(BORRADOR);
    String cuerpo =
        avance(
            version,
            """
        "identificacion":{"tipologia":"arco"}
        """);
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .header(InspeccionController.IDEMPOTENCIA, "avance-1")
                .content(cuerpo)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(version + 1));
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .header(InspeccionController.IDEMPOTENCIA, "avance-1")
                .content(cuerpo)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(version + 1));
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("version-desactualizada")));
  }

  @Test
  void syncConVersionViejaGanaElClienteYGuardaCopia() throws Exception {
    long version = version(BORRADOR);
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    avance(
                        version,
                        """
                    "identificacion":{"tipologia":"servidor"}
                    """))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk());
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .header(InspeccionController.SYNC, "true")
                .content(
                    avance(
                        version,
                        """
                    "identificacion":{"tipologia":"dispositivo"}
                    """))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datos.identificacion.tipologia").value("dispositivo"))
        .andExpect(jsonPath("$.sincronizada_en").isNotEmpty());
    assertThat(
            jdbc.sql("select count(*) from copia_conflicto where inspeccion_id = :id")
                .param("id", BORRADOR)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select datos -> 'identificacion' ->> 'tipologia' from copia_conflicto where inspeccion_id = :id")
                .param("id", BORRADOR)
                .query(String.class)
                .single())
        .isEqualTo("servidor");
  }

  @Test
  void acompanantesSeReemplazanYElAutorNoPuedeSerloNiElAcompananteVe() throws Exception {
    mvc.perform(
            put(BASE + BORRADOR + "/acompanantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acompanantes":[{"nombre":"Par de campo","usuario_id":"%s"},\
                    {"nombre":"Externo"}]}
                    """
                        .formatted(E2))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acompanantes.length()").value(2));
    mvc.perform(
            put(BASE + BORRADOR + "/acompanantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acompanantes":[{"nombre":"Solo uno"}]}
                    """)
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acompanantes.length()").value(1))
        .andExpect(jsonPath("$.acompanantes[0].nombre").value("Solo uno"));
    mvc.perform(
            put(BASE + BORRADOR + "/acompanantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acompanantes":[{"nombre":"Yo","usuario_id":"%s"}]}
                    """
                        .formatted(E1))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get(BASE + BORRADOR).with(como(Rol.ESTUDIANTE, E2)))
        .andExpect(status().isNotFound());
  }

  @Test
  void eliminarEsLogicoAuditadoIdempotenteYSoloElAutorEnBorrador() throws Exception {
    mvc.perform(delete(BASE + BORRADOR_E2).with(csrf()).with(como(Rol.CATEDRATICO, C1)))
        .andExpect(status().isNotFound());
    mvc.perform(delete(BASE + ENVIADA).with(csrf()).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isConflict());
    mvc.perform(delete(BASE + PUBLICADA).with(csrf()).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isConflict());
    mvc.perform(delete(BASE + BORRADOR).with(csrf()).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.sql("select eliminado_en from inspeccion where id = :id")
                .param("id", BORRADOR)
                .query(Instant.class)
                .optional())
        .isPresent();
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from auditoria
                    where entidad = 'inspeccion' and entidad_id = :id and accion = 'DAR_DE_BAJA'
                    """)
                .param("id", BORRADOR)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    mvc.perform(get("/api/v1/inspecciones").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(
            jsonPath("$.content[*].id")
                .value(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(BORRADOR.toString()))));
    mvc.perform(delete(BASE + BORRADOR).with(csrf()).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isNotFound());
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from auditoria
                    where entidad = 'inspeccion' and entidad_id = :id and accion = 'DAR_DE_BAJA'
                    """)
                .param("id", BORRADOR)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void avisoALos180DiasNoRepiteNiTocaActividadYElCandadoDejaUnaSolaCorrida() {
    Instant ahora = Instant.parse("2026-10-25T10:00:00Z");
    Instant actividad = ahora.minus(181, ChronoUnit.DAYS);
    jdbc.sql(
            "update inspeccion set ultima_actividad_en = :act, aviso_abandono_en = null where id = :id")
        .param("act", Timestamp.from(actividad))
        .param("id", BORRADOR)
        .update();
    assertThat(avisos.avisar(ahora)).isEqualTo(1);
    Instant aviso =
        jdbc.sql("select aviso_abandono_en from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Instant.class)
            .single();
    Instant actividadTrasAviso =
        jdbc.sql("select ultima_actividad_en from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Instant.class)
            .single();
    assertThat(aviso).isEqualTo(ahora);
    assertThat(actividadTrasAviso).isEqualTo(actividad);
    assertThat(
            jdbc.sql("select tipo from notificacion where usuario_id = :id")
                .param("id", E1)
                .query(String.class)
                .list())
        .contains("BORRADOR_ABANDONADO");
    assertThat(avisos.avisar(ahora.plus(1, ChronoUnit.DAYS))).isZero();

    jdbc.sql(
            "update inspeccion set ultima_actividad_en = :act, aviso_abandono_en = null where id = :id")
        .param("act", Timestamp.from(ahora.minus(179, ChronoUnit.DAYS)))
        .param("id", BORRADOR_E2)
        .update();
    assertThat(avisos.avisar(ahora)).isZero();

    SimpleLock candado =
        candados
            .lock(
                new LockConfiguration(
                    Instant.now(),
                    "aviso-borradores-abandonados",
                    Duration.ofMinutes(1),
                    Duration.ZERO))
            .orElseThrow();
    try {
      job.ejecutar();
    } finally {
      candado.unlock();
    }
  }

  @Test
  void guardarLimpiaElAvisoYLaActividadDelAutorNoPasaPorElVersionadoDeFotos() throws Exception {
    Instant ahora = Instant.parse("2026-10-25T10:00:00Z");
    jdbc.sql("update inspeccion set aviso_abandono_en = :ahora where id = :id")
        .param("ahora", Timestamp.from(ahora))
        .param("id", BORRADOR)
        .update();
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    avance(
                        version(BORRADOR),
                        """
                    "identificacion":{"tipologia":"losas"}
                    """))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.sql("select aviso_abandono_en from inspeccion where id = :id")
                .param("id", BORRADOR)
                .query(Instant.class)
                .optional())
        .isEmpty();
    Instant antes =
        jdbc.sql("select ultima_actividad_en from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Instant.class)
            .single();
    contrato.registrarActividadAutor(BORRADOR);
    Instant despues =
        jdbc.sql("select ultima_actividad_en from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Instant.class)
            .single();
    assertThat(despues).isAfter(antes);
    assertThat(contrato.exigirBorradorPropio(BORRADOR, E1))
        .isEqualTo(InspeccionConsultaTest.LAS_ROSAS);
  }

  private long version(UUID id) throws Exception {
    MvcResult resultado =
        mvc.perform(get(BASE + id).with(como(Rol.ESTUDIANTE, E1)))
            .andExpect(status().isOk())
            .andReturn();
    String cuerpo = resultado.getResponse().getContentAsString();
    int i = cuerpo.indexOf("\"version\":");
    return Long.parseLong(cuerpo.substring(i + 10, cuerpo.indexOf(',', i + 10)).trim());
  }

  private static String avance(long version, String datos) {
    return "{\"datos\":{" + datos + "},\"version\":" + version + "}";
  }
}
