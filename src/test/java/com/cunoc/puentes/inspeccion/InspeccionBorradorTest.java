package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import com.jayway.jsonpath.JsonPath;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
  @Autowired PlatformTransactionManager gestorTransacciones;

  @Test
  void guardarAvanceParcialSubeVersionYActividadSinExigirObligatorios() throws Exception {
    // La actividad real usa la JVM; el now() del fixture corre en otro reloj (PostgreSQL).
    jdbc.sql("update inspeccion set ultima_actividad_en = :actividad where id = :id")
        .param("actividad", Timestamp.from(Instant.now().minusSeconds(1)))
        .param("id", BORRADOR)
        .update();
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
  void claveMayorA100CaracteresConservaEl400SinGuardarEfectos() throws Exception {
    long version = version(BORRADOR);
    EstadoInspeccionAntes antes = estadoAntes(BORRADOR);
    String clave = "k".repeat(101);
    MvcResult respuesta =
        putConClave(
            BORRADOR,
            clave,
            avance(version, "\"identificacion\":{\"tipologia\":\"losas\"}"),
            false,
            E1,
            Rol.ESTUDIANTE);

    assertThat(respuesta.getResponse().getStatus()).isEqualTo(400);
    assertThat(tipo(respuesta)).endsWith("idempotencia-invalida");
    assertThat(estadoAntes(BORRADOR)).isEqualTo(antes);
    assertThat(cantidadResultados(clave)).isZero();
  }

  @Test
  void claveCon100CaracteresUnicodeEsValidaY101NoGuardaEfectos() throws Exception {
    long version = version(BORRADOR);
    String claveValida = "😀".repeat(100);
    String claveLarga = "🚲".repeat(101);
    MvcResult guardada =
        putConClave(
            BORRADOR,
            claveValida,
            avance(version, "\"identificacion\":{\"tipologia\":\"losas\"}"),
            false,
            E1,
            Rol.ESTUDIANTE);
    long versionGuardada = version(BORRADOR);
    EstadoInspeccionAntes antesDelRechazo = estadoAntes(BORRADOR);
    MvcResult rechazada =
        putConClave(
            BORRADOR,
            claveLarga,
            avance(versionGuardada, "\"identificacion\":{\"tipologia\":\"arco\"}"),
            false,
            E1,
            Rol.ESTUDIANTE);

    assertThat(guardada.getResponse().getStatus()).isEqualTo(200);
    assertThat(versionGuardada).isEqualTo(version + 1);
    assertThat(rechazada.getResponse().getStatus()).isEqualTo(400);
    assertThat(tipo(rechazada)).endsWith("idempotencia-invalida");
    assertThat(estadoAntes(BORRADOR)).isEqualTo(antesDelRechazo);
    assertThat(cantidadResultados(claveValida)).isEqualTo(1);
    assertThat(cantidadResultados(claveLarga)).isZero();
  }

  @Test
  void claveBlancaConservaLaEscrituraSinIdempotencia() throws Exception {
    long version = version(BORRADOR);
    MvcResult respuesta =
        putConClave(
            BORRADOR,
            "   ",
            avance(version, "\"identificacion\":{\"tipologia\":\"losas\"}"),
            false,
            E1,
            Rol.ESTUDIANTE);

    assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
    assertThat(version(BORRADOR)).isEqualTo(version + 1);
    assertThat(cantidadResultados("")).isZero();
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

  @Test
  void replayConservaBytesYCanonizaObjetosYNumerosPeroRespetaOrdenDeArrays() throws Exception {
    long version = version(BORRADOR);
    String clave = "put-canonicidad";
    String primero =
        """
        {"datos":{"z":[1.0,2.00],"a":{"b":1.0,"a":2}},"version":%d}
        """
            .formatted(version);
    String equivalente =
        """
        {"version":%d,"datos":{"a":{"a":2.0,"b":1e0},"z":[1e0,2.000e0]}}
        """
            .formatted(version);

    MvcResult original = putConClave(BORRADOR, clave, primero, false, E1, Rol.ESTUDIANTE);
    MvcResult replay = putConClave(BORRADOR, clave, equivalente, false, E1, Rol.ESTUDIANTE);

    assertThat(original.getResponse().getStatus()).isEqualTo(200);
    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(replay.getResponse().getContentAsByteArray())
        .containsExactly(original.getResponse().getContentAsByteArray());
    assertThat(version(BORRADOR)).isEqualTo(version + 1);
    assertThat(cantidadResultados(clave)).isEqualTo(1);

    String arrayReordenado =
        """
        {"datos":{"z":[2,1],"a":{"b":1,"a":2}},"version":%d}
        """
            .formatted(version);
    MvcResult incompatible =
        putConClave(BORRADOR, clave, arrayReordenado, false, E1, Rol.ESTUDIANTE);
    assertThat(incompatible.getResponse().getStatus()).isEqualTo(409);
    assertThat(tipo(incompatible)).endsWith("clave-idempotencia-incompatible");
    assertThat(version(BORRADOR)).isEqualTo(version + 1);
  }

  @Test
  void claveVinculaPayloadSyncYRecursoSinMutarTrasIncompatibilidad() throws Exception {
    jdbc.sql("update inspeccion set estado = 'BORRADOR' where id = :id")
        .param("id", CAMBIOS)
        .update();
    long version = version(BORRADOR);
    String clave = "put-huella-completa";
    String cuerpo = avance(version, "\"identificacion\":{\"tipologia\":\"losas\"}");
    MvcResult original = putConClave(BORRADOR, clave, cuerpo, false, E1, Rol.ESTUDIANTE);
    EstadoInspeccionAntes inspeccionOriginal = estadoAntes(BORRADOR);
    EstadoInspeccionAntes otroRecurso = estadoAntes(CAMBIOS);
    long copias = copias(BORRADOR);
    long resultados = cantidadResultados(clave);

    MvcResult payloadDistinto =
        putConClave(
            BORRADOR,
            clave,
            avance(version, "\"identificacion\":{\"tipologia\":\"arco\"}"),
            false,
            E1,
            Rol.ESTUDIANTE);
    MvcResult syncDistinto = putConClave(BORRADOR, clave, cuerpo, true, E1, Rol.ESTUDIANTE);
    MvcResult recursoDistinto =
        putConClave(
            CAMBIOS,
            clave,
            avance(version(CAMBIOS), "\"identificacion\":{\"tipologia\":\"losas\"}"),
            false,
            E1,
            Rol.ESTUDIANTE);

    for (MvcResult respuesta : List.of(payloadDistinto, syncDistinto, recursoDistinto)) {
      assertThat(respuesta.getResponse().getStatus()).isEqualTo(409);
      assertThat(tipo(respuesta)).endsWith("clave-idempotencia-incompatible");
    }
    assertThat(original.getResponse().getStatus()).isEqualTo(200);
    assertThat(estadoAntes(BORRADOR)).isEqualTo(inspeccionOriginal);
    assertThat(estadoAntes(CAMBIOS)).isEqualTo(otroRecurso);
    assertThat(copias(BORRADOR)).isEqualTo(copias);
    assertThat(cantidadResultados(clave)).isEqualTo(resultados);
  }

  @Test
  void permisoYTitularidadSeCompruebanAntesDelReplay() throws Exception {
    long version = version(BORRADOR);
    String clave = "put-autorizacion-previa";
    String cuerpo = avance(version, "\"identificacion\":{\"tipologia\":\"losas\"}");
    MvcResult original = putConClave(BORRADOR, clave, cuerpo, false, E1, Rol.ESTUDIANTE);

    MvcResult rolRevocado = putConClave(BORRADOR, clave, cuerpo, false, E1, Rol.ADMINISTRADOR);
    assertThat(rolRevocado.getResponse().getStatus()).isEqualTo(403);
    jdbc.sql("update inspeccion set autor_id = :nuevo where id = :id")
        .param("nuevo", E2)
        .param("id", BORRADOR)
        .update();
    MvcResult sinTitularidad = putConClave(BORRADOR, clave, cuerpo, false, E1, Rol.ESTUDIANTE);

    assertThat(original.getResponse().getStatus()).isEqualTo(200);
    assertThat(sinTitularidad.getResponse().getStatus()).isEqualTo(404);
    assertThat(cantidadResultados(clave)).isEqualTo(1);
  }

  @Test
  void rechazosTerminalesDePayloadSeCacheanYSeReproducenBytePorByte() throws Exception {
    long version = version(BORRADOR);
    List<RechazoEsperado> casos =
        List.of(
            new RechazoEsperado(
                "put-inmutable",
                "{\"datos\":{},\"version\":%d,\"puente_id\":\"%s\"}"
                    .formatted(version, InspeccionConsultaTest.LAS_ROSAS),
                422,
                "campo-inmutable"),
            new RechazoEsperado(
                "put-tamano",
                "{\"datos\":{\"identificacion\":{\"tipologia\":\"%s\"}},\"version\":%d}"
                    .formatted("x".repeat(300_000), version),
                413,
                "cuerpo-demasiado-grande"),
            new RechazoEsperado(
                "put-grupo",
                "{\"datos\":{\"subestructura\":{\"estribo_entrada\":{\"calificacion\":2}}},"
                    + "\"version\":%d,\"grupos_sin_danos\":[\"subestructura.estribo_entrada\"]}"
                        .formatted(version),
                422,
                "grupo-con-danos"),
            new RechazoEsperado(
                "put-datos",
                "{\"datos\":{\"identificacion\":{\"numero_tramos\":0}},\"version\":%d}"
                    .formatted(version),
                422,
                "datos-invalidos"),
            new RechazoEsperado(
                "put-fecha",
                "{\"datos\":{},\"version\":%d,\"fecha_inspeccion\":\"2999-01-01\"}"
                    .formatted(version),
                422,
                "fecha-inspeccion-invalida"));

    for (RechazoEsperado caso : casos) {
      MvcResult primera =
          putConClave(BORRADOR, caso.clave(), caso.cuerpo(), false, E1, Rol.ESTUDIANTE);
      MvcResult replay =
          putConClave(BORRADOR, caso.clave(), caso.cuerpo(), false, E1, Rol.ESTUDIANTE);
      assertThat(primera.getResponse().getStatus()).isEqualTo(caso.estado());
      assertThat(tipo(primera)).endsWith(caso.tipo());
      assertThat(replay.getResponse().getStatus()).isEqualTo(caso.estado());
      assertThat(replay.getResponse().getContentAsByteArray())
          .containsExactly(primera.getResponse().getContentAsByteArray());
      assertThat(cantidadResultados(caso.clave())).isEqualTo(1);
    }
  }

  @Test
  void rechazoDePayloadSyncConVersionViejaNoConfirmaLaCopiaDeConflicto() throws Exception {
    long versionAnterior = version(BORRADOR);
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(avance(versionAnterior, "\"identificacion\":{\"tipologia\":\"servidor\"}"))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk());
    EstadoInspeccionAntes antes = estadoAntes(BORRADOR);
    long auditoriasAntes = auditorias(BORRADOR);
    long copiasAntes = copias(BORRADOR);
    long versionActual = version(BORRADOR);
    String clave = "put-sync-payload-rechazado";
    String invalido =
        "{\"datos\":{\"subestructura\":{\"estribo_entrada\":{\"calificacion\":2}}},"
            + "\"version\":%d,\"grupos_sin_danos\":[\"subestructura.estribo_entrada\"]}"
                .formatted(versionAnterior);

    MvcResult primera = putConClave(BORRADOR, clave, invalido, true, E1, Rol.ESTUDIANTE);
    MvcResult replay = putConClave(BORRADOR, clave, invalido, true, E1, Rol.ESTUDIANTE);

    assertThat(primera.getResponse().getStatus()).isEqualTo(422);
    assertThat(tipo(primera)).endsWith("grupo-con-danos");
    assertThat(replay.getResponse().getStatus()).isEqualTo(422);
    assertThat(replay.getResponse().getContentAsByteArray())
        .containsExactly(primera.getResponse().getContentAsByteArray());
    assertThat(estadoAntes(BORRADOR)).isEqualTo(antes);
    assertThat(version(BORRADOR)).isEqualTo(versionActual);
    assertThat(copias(BORRADOR)).isEqualTo(copiasAntes);
    assertThat(auditorias(BORRADOR)).isEqualTo(auditoriasAntes);
    assertThat(cantidadResultados(clave)).isEqualTo(1);
  }

  @Test
  void erroresDependientesDelEstadoNoSeCachean() throws Exception {
    long versionAnterior = version(BORRADOR);
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(avance(versionAnterior, "\"identificacion\":{\"tipologia\":\"servidor\"}"))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk());
    String claveVersion = "put-version-desactualizada";
    String avanceViejo =
        avance(versionAnterior, "\"identificacion\":{\"tipologia\":\"dispositivo\"}");
    MvcResult versionVieja =
        putConClave(BORRADOR, claveVersion, avanceViejo, false, E1, Rol.ESTUDIANTE);
    assertThat(versionVieja.getResponse().getStatus()).isEqualTo(409);
    assertThat(tipo(versionVieja)).endsWith("version-desactualizada");
    assertThat(cantidadResultados(claveVersion)).isZero();
    MvcResult versionVigente =
        putConClave(
            BORRADOR,
            claveVersion,
            avance(version(BORRADOR), "\"identificacion\":{\"tipologia\":\"dispositivo\"}"),
            false,
            E1,
            Rol.ESTUDIANTE);
    assertThat(versionVigente.getResponse().getStatus()).isEqualTo(200);
    assertThat(cantidadResultados(claveVersion)).isEqualTo(1);

    String claveEstado = "put-no-editable";
    String cuerpo = avance(version(ENVIADA), "\"identificacion\":{\"tipologia\":\"losas\"}");
    MvcResult noEditable = putConClave(ENVIADA, claveEstado, cuerpo, false, E1, Rol.ESTUDIANTE);
    assertThat(noEditable.getResponse().getStatus()).isEqualTo(409);
    assertThat(tipo(noEditable)).endsWith("inspeccion-no-editable");
    assertThat(cantidadResultados(claveEstado)).isZero();
    jdbc.sql("update inspeccion set estado = 'BORRADOR' where id = :id")
        .param("id", ENVIADA)
        .update();
    MvcResult ahoraEditable = putConClave(ENVIADA, claveEstado, cuerpo, false, E1, Rol.ESTUDIANTE);
    assertThat(ahoraEditable.getResponse().getStatus()).isEqualTo(200);
    assertThat(cantidadResultados(claveEstado)).isEqualTo(1);
    jdbc.sql("update inspeccion set estado = 'CAMBIOS_SOLICITADOS' where id = :id")
        .param("id", ENVIADA)
        .update();
    MvcResult replayNoEditable =
        putConClave(ENVIADA, claveEstado, cuerpo, false, E1, Rol.ESTUDIANTE);
    assertThat(replayNoEditable.getResponse().getStatus()).isEqualTo(200);
    assertThat(replayNoEditable.getResponse().getContentAsByteArray())
        .containsExactly(ahoraEditable.getResponse().getContentAsByteArray());
  }

  @Test
  void llamadasHttpConcurrentesConLaMismaClaveGuardanUnaVersionYCopia() throws Exception {
    long versionAnterior = version(BORRADOR);
    mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(avance(versionAnterior, "\"identificacion\":{\"tipologia\":\"servidor\"}"))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk());
    long versionActual = version(BORRADOR);
    String clave = "put-concurrencia-sync";
    String cuerpo = avance(versionAnterior, "\"identificacion\":{\"tipologia\":\"dispositivo\"}");
    ExecutorService ejecutor = Executors.newFixedThreadPool(2);
    CountDownLatch iniciadas = new CountDownLatch(2);
    CountDownLatch liberar = new CountDownLatch(1);
    AtomicReference<Future<MvcResult>> primera = new AtomicReference<>();
    AtomicReference<Future<MvcResult>> segunda = new AtomicReference<>();
    TransactionTemplate transaccion = new TransactionTemplate(gestorTransacciones);

    try {
      transaccion.executeWithoutResult(
          estado -> {
            jdbc.sql("select id from inspeccion where id = :id for update")
                .param("id", BORRADOR)
                .query(UUID.class)
                .single();
            primera.set(
                ejecutor.submit(
                    () -> {
                      iniciadas.countDown();
                      liberar.await();
                      return putConClave(BORRADOR, clave, cuerpo, true, E1, Rol.ESTUDIANTE);
                    }));
            segunda.set(
                ejecutor.submit(
                    () -> {
                      iniciadas.countDown();
                      liberar.await();
                      return putConClave(BORRADOR, clave, cuerpo, true, E1, Rol.ESTUDIANTE);
                    }));
            try {
              assertThat(iniciadas.await(3, TimeUnit.SECONDS)).isTrue();
              liberar.countDown();
              Thread.sleep(150);
              assertThat(primera.get().isDone()).isFalse();
              assertThat(segunda.get().isDone()).isFalse();
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException("La prueba fue interrumpida.", error);
            }
          });
      MvcResult respuesta1 = primera.get().get(5, TimeUnit.SECONDS);
      MvcResult respuesta2 = segunda.get().get(5, TimeUnit.SECONDS);

      assertThat(respuesta1.getResponse().getStatus()).isEqualTo(200);
      assertThat(respuesta2.getResponse().getStatus()).isEqualTo(200);
      assertThat(respuesta2.getResponse().getContentAsByteArray())
          .containsExactly(respuesta1.getResponse().getContentAsByteArray());
      assertThat(version(BORRADOR)).isEqualTo(versionActual + 1);
      assertThat(copias(BORRADOR)).isEqualTo(1);
      assertThat(cantidadResultados(clave)).isEqualTo(1);
    } finally {
      liberar.countDown();
      ejecutor.shutdownNow();
      assertThat(ejecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void esperaDeDosSegundosDevuelve409SinCachearYPermiteReintento() throws Exception {
    String clave = "put-timeout";
    long version = version(BORRADOR);
    String cuerpo = avance(version, "\"identificacion\":{\"tipologia\":\"losas\"}");
    long lock = claveBloqueo(E1, clave);
    ExecutorService ejecutor = Executors.newSingleThreadExecutor();
    TransactionTemplate transaccion = new TransactionTemplate(gestorTransacciones);
    try {
      transaccion.executeWithoutResult(
          estado -> {
            assertThat(
                    jdbc.sql("select pg_try_advisory_xact_lock(:llave)")
                        .param("llave", lock)
                        .query(Boolean.class)
                        .single())
                .isTrue();
            Future<MvcResult> resultado =
                ejecutor.submit(
                    () -> putConClave(BORRADOR, clave, cuerpo, false, E1, Rol.ESTUDIANTE));
            try {
              MvcResult respuesta = resultado.get(5, TimeUnit.SECONDS);
              assertThat(respuesta.getResponse().getStatus()).isEqualTo(409);
              assertThat(tipo(respuesta)).endsWith("operacion-en-proceso");
              assertThat(cantidadResultados(clave)).isZero();
              assertThat(version(BORRADOR)).isEqualTo(version);
            } catch (Exception error) {
              throw new IllegalStateException("No se pudo comprobar el timeout HTTP.", error);
            }
          });
      MvcResult reintento = putConClave(BORRADOR, clave, cuerpo, false, E1, Rol.ESTUDIANTE);
      assertThat(reintento.getResponse().getStatus()).isEqualTo(200);
      assertThat(cantidadResultados(clave)).isEqualTo(1);
    } finally {
      ejecutor.shutdownNow();
      assertThat(ejecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void filaBinariaDePutNoSeReinterpretaComoRespuestaJsonbDelPostEnvio() throws Exception {
    long version = version(BORRADOR);
    String clave = "put-no-es-post";
    String cuerpo = avance(version, "\"identificacion\":{\"tipologia\":\"losas\"}");
    MvcResult guardado = putConClave(BORRADOR, clave, cuerpo, false, E1, Rol.ESTUDIANTE);
    EstadoInspeccionAntes antes = estadoAntes(BORRADOR);

    MvcResult envio =
        mvc.perform(
                post(BASE + BORRADOR + "/envio")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(InspeccionController.IDEMPOTENCIA, clave)
                    .content("{}")
                    .with(csrf())
                    .with(como(Rol.ESTUDIANTE, E1)))
            .andReturn();

    assertThat(guardado.getResponse().getStatus()).isEqualTo(200);
    assertThat(envio.getResponse().getStatus()).isEqualTo(409);
    assertThat(tipo(envio)).endsWith("clave-idempotencia-incompatible");
    assertThat(estadoAntes(BORRADOR)).isEqualTo(antes);
    assertThat(cantidadResultados(clave)).isEqualTo(1);
  }

  private MvcResult putConClave(
      UUID inspeccion, String clave, String cuerpo, boolean sync, UUID actor, Rol rol)
      throws Exception {
    var peticion =
        put(BASE + inspeccion)
            .contentType(MediaType.APPLICATION_JSON)
            .header(InspeccionController.IDEMPOTENCIA, clave)
            .content(cuerpo);
    if (sync) {
      peticion.header(InspeccionController.SYNC, "true");
    }
    return mvc.perform(peticion.with(csrf()).with(como(rol, actor))).andReturn();
  }

  private EstadoInspeccionAntes estadoAntes(UUID id) {
    return jdbc.sql(
            "select datos::text, version, estado, ultima_actividad_en::text, sincronizada_en::text "
                + "from inspeccion where id = :id")
        .param("id", id)
        .query(
            (rs, fila) ->
                new EstadoInspeccionAntes(
                    rs.getString(1),
                    rs.getLong(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5)))
        .single();
  }

  private long cantidadResultados(String clave) {
    return jdbc.sql(
            "select count(*) from idempotencia where usuario_id = :usuario and clave = :clave")
        .param("usuario", E1)
        .param("clave", clave)
        .query(Long.class)
        .single();
  }

  private long copias(UUID id) {
    return jdbc.sql("select count(*) from copia_conflicto where inspeccion_id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private long auditorias(UUID id) {
    return jdbc.sql(
            "select count(*) from auditoria where entidad = 'inspeccion' and entidad_id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private byte[] bytesResultado(String clave) {
    return jdbc.sql(
            "select respuesta_bytes from idempotencia where usuario_id = :usuario and clave = :clave")
        .param("usuario", E1)
        .param("clave", clave)
        .query(byte[].class)
        .single();
  }

  private static String tipo(MvcResult resultado) throws Exception {
    return JsonPath.read(resultado.getResponse().getContentAsString(), "$.type");
  }

  private static long claveBloqueo(UUID usuarioId, String clave) throws Exception {
    byte[] claveBytes = clave.getBytes(StandardCharsets.UTF_8);
    byte[] identidad =
        ByteBuffer.allocate(Long.BYTES * 2 + Integer.BYTES + claveBytes.length)
            .putLong(usuarioId.getMostSignificantBits())
            .putLong(usuarioId.getLeastSignificantBits())
            .putInt(claveBytes.length)
            .put(claveBytes)
            .array();
    return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(identidad)).getLong();
  }

  private record RechazoEsperado(String clave, String cuerpo, int estado, String tipo) {}

  private record EstadoInspeccionAntes(
      String datos, long version, String estado, String actividad, String sincronizada) {}

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
