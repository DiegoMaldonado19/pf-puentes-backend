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
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.EnviarInspeccionDTO;
import com.cunoc.puentes.usuario.Rol;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/inspeccion/consulta.sql", "/inspeccion/borrador.sql"})
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionEnvioTest extends PruebaIntegracion {

  static final UUID E1 = InspeccionConsultaTest.E1;
  static final UUID E2 = InspeccionConsultaTest.E2;
  static final UUID C1 = InspeccionConsultaTest.C1;
  static final UUID C2 = InspeccionConsultaTest.C2;
  static final UUID PROFESIONAL = InspeccionConsultaTest.PROFESIONAL;
  static final UUID BORRADOR = InspeccionConsultaTest.BORRADOR_E1;
  static final UUID BORRADOR_PRO = InspeccionConsultaTest.BORRADOR_PRO;
  static final UUID ENVIADA = InspeccionConsultaTest.ENVIADA;
  static final UUID CAMBIOS = InspeccionConsultaTest.CAMBIOS;
  static final UUID FORMULARIO = InspeccionConsultaTest.id(0x21);
  static final UUID FORMULARIO_NUEVO = InspeccionConsultaTest.id(0x22);
  static final String BASE = "/api/v1/inspecciones/";
  public static final String DATOS_COMPLETOS =
      """
      {"identificacion":{"tipologia":"losas","material_principal":"concreto",\
      "numero_tramos":2,"longitud_m":18.0,"ancho_calzada_m":6.0,"anio_construccion":1998},\
      "subestructura":{"estribo_entrada":{"calificacion":1,"fisuras":0,"humedad":0,\
      "desconchado":0,"asentamiento":0,"socavacion":0},"estribo_salida":{"calificacion":1}},\
      "superestructura":{"losa":{"calificacion":2}},\
      "accesos":{"entrada":{"calificacion":1}},"cauce":{"calificacion":2}}
      """;
  static final String DATOS_SIN_TRES =
      """
      {"identificacion":{"tipologia":"losas","material_principal":"concreto",\
      "longitud_m":18.0,"ancho_calzada_m":6.0,"anio_construccion":1998},\
      "subestructura":{"estribo_entrada":{"calificacion":1,"fisuras":0,"humedad":0,\
      "desconchado":0,"asentamiento":0,"socavacion":0},"estribo_salida":{"calificacion":1}},\
      "superestructura":{"losa":{"calificacion":2}}}
      """;

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired EnvioInspeccionService envio;
  @Autowired org.springframework.transaction.PlatformTransactionManager gestor;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.cunoc.puentes.sync.IdempotenciaRepository resultados;

  @Test
  void fallaAlGuardarResultadoRevierteEnvioPublicacionYAvisos() throws Exception {
    for (boolean profesional : List.of(false, true)) {
      UUID recurso = profesional ? BORRADOR_PRO : BORRADOR;
      UUID actor = profesional ? PROFESIONAL : E1;
      Rol rol = profesional ? Rol.PROFESIONAL_EXTERNO : Rol.ESTUDIANTE;
      preparar(recurso, DATOS_COMPLETOS, 4, false);
      List<String> antes = instantanea(recurso);
      org.mockito.Mockito.doThrow(
              new org.springframework.dao.DataIntegrityViolationException("fallo resultado"))
          .when(resultados)
          .save(org.mockito.ArgumentMatchers.any());
      try {
        assertThat(conClave(recurso, "fallo", "{}", false, actor, rol).getResponse().getStatus())
            .isEqualTo(500);
      } finally {
        org.mockito.Mockito.reset(resultados);
      }
      assertThat(instantanea(recurso)).isEqualTo(antes);
      // Los dobles registran invocaciones en memoria; no representan efectos persistidos.
      assertThat(conClave(recurso, "fallo", "{}", false, actor, rol).getResponse().getStatus())
          .isEqualTo(200);
    }
  }

  @Test
  void timeoutDeDosSegundosNoCacheaYPermiteReintentar() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    String clave = "envio-timeout";
    byte[] bytes = clave.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] identidad =
        java.nio.ByteBuffer.allocate(20 + bytes.length)
            .putLong(E1.getMostSignificantBits())
            .putLong(E1.getLeastSignificantBits())
            .putInt(bytes.length)
            .put(bytes)
            .array();
    long bloqueo =
        java.nio.ByteBuffer.wrap(
                java.security.MessageDigest.getInstance("SHA-256").digest(identidad))
            .getLong();
    List<String> antes = instantanea(BORRADOR);
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      new org.springframework.transaction.support.TransactionTemplate(gestor)
          .executeWithoutResult(
              tx -> {
                jdbc.sql("select pg_advisory_xact_lock(:llave)")
                    .param("llave", bloqueo)
                    .query(Object.class)
                    .single();
                var pendiente =
                    ejecutor.submit(
                        () -> conClave(BORRADOR, clave, "{}", false, E1, Rol.ESTUDIANTE));
                long inicio = System.nanoTime();
                try {
                  exigirError(pendiente.get(5, TimeUnit.SECONDS), 409, "operacion-en-proceso");
                  assertThat(java.time.Duration.ofNanos(System.nanoTime() - inicio))
                      .isGreaterThanOrEqualTo(java.time.Duration.ofMillis(1900));
                  assertThat(instantanea(BORRADOR)).isEqualTo(antes);
                } catch (Exception error) {
                  throw new IllegalStateException(error);
                }
              });
    }
    assertThat(conClave(BORRADOR, clave, "{}", false, E1, Rol.ESTUDIANTE).getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void dosPeticionesHttpConMismaClaveProducenUnaSolaTransicionYAviso() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    CountDownLatch iniciar = new CountDownLatch(1);
    Callable<MvcResult> peticion =
        () -> {
          iniciar.await();
          return conClave(BORRADOR, "concurrente", "{}", false, E1, Rol.ESTUDIANTE);
        };
    List<String> antes = instantanea(BORRADOR);
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var primera = ejecutor.submit(peticion);
      var segunda = ejecutor.submit(peticion);
      iniciar.countDown();
      MvcResult una = primera.get(10, TimeUnit.SECONDS);
      MvcResult otra = segunda.get(10, TimeUnit.SECONDS);
      assertThat(una.getResponse().getStatus()).isEqualTo(200);
      assertThat(otra.getResponse().getStatus()).isEqualTo(200);
      assertThat(otra.getResponse().getContentAsByteArray())
          .containsExactly(una.getResponse().getContentAsByteArray());
    }
    List<String> despues = instantanea(BORRADOR);
    assertThat(Long.parseLong(despues.get(1))).isEqualTo(Long.parseLong(antes.get(1)) + 1);
    assertThat(Long.parseLong(despues.get(2))).isEqualTo(Long.parseLong(antes.get(2)) + 1);
    assertThat(Long.parseLong(despues.get(4))).isEqualTo(Long.parseLong(antes.get(4)) + 1);
  }

  @BeforeEach
  void reiniciarOyentes() {
    OyentesPublicacionDePrueba.reiniciar();
  }

  @Test
  void estudianteEnviaCompletaQuedaEnviadaYAvisaSoloASuCatedratico() throws Exception {
    Instant dispositivo = Instant.parse("2026-10-25T16:05:00Z");
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    mvc.perform(
            post(BASE + BORRADOR + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enviada_en\":\"%s\"}".formatted(dispositivo))
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ENVIADA"))
        .andExpect(jsonPath("$.enviada_en").isNotEmpty())
        .andExpect(jsonPath("$.sincronizada_en").isNotEmpty())
        .andExpect(jsonPath("$.publicada_en").doesNotExist())
        .andExpect(jsonPath("$.indice_condicion").doesNotExist());
    assertThat(
            jdbc.sql("select enviada_en from inspeccion where id = :id")
                .param("id", BORRADOR)
                .query(Instant.class)
                .single())
        .isEqualTo(dispositivo);
    mvc.perform(get(BASE + BORRADOR + "/historial").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].estado_origen").value("BORRADOR"))
        .andExpect(jsonPath("$[0].estado_destino").value("ENVIADA"))
        .andExpect(jsonPath("$[0].actor_id").value(E1.toString()));
    assertThat(
            jdbc.sql(
                    """
                    select valor_anterior->>'estado', valor_nuevo->>'estado'
                    from auditoria
                    where entidad = 'inspeccion' and entidad_id = :id and accion = 'CAMBIAR_ESTADO'
                    """)
                .param("id", BORRADOR)
                .query((rs, n) -> rs.getString(1) + "->" + rs.getString(2))
                .list())
        .contains("BORRADOR->ENVIADA");
    assertThat(
            jdbc.sql("select usuario_id, tipo from notificacion where tipo = :tipo")
                .param("tipo", AvisoInspeccionEnviada.TIPO)
                .query((rs, n) -> rs.getObject(1, UUID.class) + ":" + rs.getString(2))
                .list())
        .containsExactly(C1 + ":" + AvisoInspeccionEnviada.TIPO)
        .doesNotContain(C2 + ":" + AvisoInspeccionEnviada.TIPO);
    Instant sincronizada =
        jdbc.sql("select sincronizada_en from inspeccion where id = :id")
            .param("id", BORRADOR)
            .query(Instant.class)
            .single();
    assertThat(sincronizada).isAfter(dispositivo);
  }

  @Test
  void envioConLaMismaClaveReproduceBytesExactosSinDuplicarTransicion() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    long transicionesAntes =
        jdbc.sql("select count(*) from transicion_inspeccion where inspeccion_id = :id")
            .param("id", BORRADOR)
            .query(Long.class)
            .single();
    long avisosAntes =
        jdbc.sql("select count(*) from notificacion where usuario_id = :id and tipo = :tipo")
            .param("id", C1)
            .param("tipo", AvisoInspeccionEnviada.TIPO)
            .query(Long.class)
            .single();

    MvcResult primera =
        mvc.perform(
                enviar(BORRADOR)
                    .header(InspeccionController.IDEMPOTENCIA, "envio-p5-repetido")
                    .with(como(Rol.ESTUDIANTE, E1)))
            .andExpect(status().isOk())
            .andReturn();
    MvcResult replay =
        mvc.perform(
                enviar(BORRADOR)
                    .header(InspeccionController.IDEMPOTENCIA, "envio-p5-repetido")
                    .with(como(Rol.ESTUDIANTE, E1)))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(replay.getResponse().getContentAsByteArray())
        .containsExactly(primera.getResponse().getContentAsByteArray());
    assertThat(estado(BORRADOR)).isEqualTo("ENVIADA");
    assertThat(
            jdbc.sql("select count(*) from transicion_inspeccion where inspeccion_id = :id")
                .param("id", BORRADOR)
                .query(Long.class)
                .single())
        .isEqualTo(transicionesAntes + 1);
    assertThat(
            jdbc.sql("select count(*) from notificacion where usuario_id = :id and tipo = :tipo")
                .param("id", C1)
                .param("tipo", AvisoInspeccionEnviada.TIPO)
                .query(Long.class)
                .single())
        .isEqualTo(avisosAntes + 1);
    assertThat(
            jdbc.sql(
                    """
                    select estado_http = 200 and respuesta is null
                       and huella_version = 1 and huella_sha256 is not null
                       and respuesta_bytes is not null
                    from idempotencia where usuario_id = :usuario and clave = :clave
                    """)
                .param("usuario", E1)
                .param("clave", "envio-p5-repetido")
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  void profesionalEnviaYPublicaEnElMismoActo() throws Exception {
    preparar(BORRADOR_PRO, DATOS_COMPLETOS, 4, false);
    mvc.perform(
            post(BASE + BORRADOR_PRO + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf())
                .with(como(Rol.PROFESIONAL_EXTERNO, PROFESIONAL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADA"))
        .andExpect(jsonPath("$.publicada_en").isNotEmpty())
        .andExpect(jsonPath("$.indice_condicion").value(65.0))
        .andExpect(jsonPath("$.estado_calculado").value("REGULAR"))
        .andExpect(jsonPath("$.estado_confirmado").value("REGULAR"));
    assertThat(
            jdbc.sql("select count(*) from notificacion where tipo = :tipo")
                .param("tipo", AvisoInspeccionEnviada.TIPO)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(OyentesPublicacionDePrueba.propuestas).contains(BORRADOR_PRO);
    assertThat(OyentesPublicacionDePrueba.hilos).contains(BORRADOR_PRO);
  }

  @Test
  void faltanCamposFotosPermanentesYFechaSeAgrupan() throws Exception {
    jdbc.sql(
            """
            update inspeccion
            set datos = CAST(:datos AS jsonb),
                datos_permanentes_confirmados_en = null,
                fecha_inspeccion = DATE '2999-01-01'
            where id = :id
            """)
        .param("datos", DATOS_SIN_TRES)
        .param("id", BORRADOR)
        .update();
    fotos(BORRADOR, 0, false);
    mvc.perform(
            post(BASE + BORRADOR + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("inspeccion-no-publicable")))
        .andExpect(jsonPath("$.title").value("La inspección no puede enviarse a revisión"))
        .andExpect(
            jsonPath("$.errores[*].campo").value(Matchers.hasItem("identificacion.numero_tramos")))
        .andExpect(jsonPath("$.errores[*].campo").value(Matchers.hasItem("cauce.calificacion")))
        .andExpect(
            jsonPath("$.errores[*].campo").value(Matchers.hasItem("accesos.entrada.calificacion")))
        .andExpect(jsonPath("$.errores[*].campo").value(Matchers.hasItem("fotos")))
        .andExpect(
            jsonPath("$.errores[*].campo")
                .value(Matchers.hasItem("datos-permanentes-sin-confirmar")))
        .andExpect(jsonPath("$.errores[*].campo").value(Matchers.hasItem("fecha_inspeccion")));
    assertThat(estado(BORRADOR)).isEqualTo("BORRADOR");
  }

  @Test
  void tresFotosOUnaPurgadaImpidenElEnvioYCuatroVigentesAlcanzan() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 3, false);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[*].campo").value(Matchers.hasItem("fotos")));
    assertThat(estado(BORRADOR)).isEqualTo("BORRADOR");

    fotos(BORRADOR, 3, true);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errores[*].campo").value(Matchers.hasItem("fotos")));

    fotos(BORRADOR, 4, false);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ENVIADA"));
  }

  @Test
  void calificacionFueraDeRangoImpideElEnvio() throws Exception {
    preparar(
        BORRADOR,
        DATOS_COMPLETOS.replace("\"cauce\":{\"calificacion\":2}", "\"cauce\":{\"calificacion\":9}"),
        4,
        false);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("inspeccion-no-publicable")))
        .andExpect(jsonPath("$.errores[*].campo").value(Matchers.hasItem("cauce.calificacion")));
    assertThat(estado(BORRADOR)).isEqualTo("BORRADOR");
  }

  @Test
  void seValidaContraLaVersionCongeladaAunqueHayaUnaActivaMasNueva() throws Exception {
    jdbc.sql("update formulario_version set estado = 'RETIRADA' where id = :id")
        .param("id", FORMULARIO)
        .update();
    jdbc.sql(
            """
            insert into formulario_version (id, codigo, esquema, estado, publicada_en)
            values (:id, 'SIECA-2', '{"type":"object"}'::jsonb, 'ACTIVA', now())
            """)
        .param("id", FORMULARIO_NUEVO)
        .update();
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formulario.id").value(FORMULARIO.toString()))
        .andExpect(jsonPath("$.estado").value("ENVIADA"));
  }

  @Test
  void enviadaNoSeEditaNiSeReenviaDuplicandoHistorialYBorrarFotoEs409() throws Exception {
    UUID foto = fotos(ENVIADA, 1, false).getFirst();
    mvc.perform(
            put(BASE + ENVIADA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datos\":{\"identificacion\":{\"tipologia\":\"x\"}},\"version\":0}")
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("inspeccion-no-editable")));
    mvc.perform(delete("/api/v1/archivos/" + foto).with(csrf()).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(Matchers.endsWith("inspeccion-no-editable")));
    long transiciones =
        jdbc.sql("select count(*) from transicion_inspeccion where inspeccion_id = :id")
            .param("id", ENVIADA)
            .query(Long.class)
            .single();
    mvc.perform(enviar(ENVIADA).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ENVIADA"));
    assertThat(
            jdbc.sql("select count(*) from transicion_inspeccion where inspeccion_id = :id")
                .param("id", ENVIADA)
                .query(Long.class)
                .single())
        .isEqualTo(transiciones);
  }

  @Test
  void reenviarTrasCambiosSolicitadosConservaElHistorialPrevio() throws Exception {
    jdbc.sql("update inspeccion set estado = 'BORRADOR' where id = :id")
        .param("id", CAMBIOS)
        .update();
    jdbc.sql(
            """
            insert into transicion_inspeccion
              (id, inspeccion_id, estado_origen, estado_destino, actor_id, creado_en)
            values
              (:t1, :id, null, 'BORRADOR', :autor, '2026-07-01T10:00:00Z'),
              (:t2, :id, 'BORRADOR', 'ENVIADA', :autor, '2026-07-02T10:00:00Z'),
              (:t3, :id, 'ENVIADA', 'EN_REVISION', :catedratico, '2026-07-03T10:00:00Z'),
              (:t4, :id, 'EN_REVISION', 'CAMBIOS_SOLICITADOS', :catedratico, '2026-07-04T10:00:00Z'),
              (:t5, :id, 'CAMBIOS_SOLICITADOS', 'BORRADOR', null, '2026-07-04T10:00:01Z')
            """)
        .param("t1", InspeccionConsultaTest.id(0xe1))
        .param("t2", InspeccionConsultaTest.id(0xe2))
        .param("t3", InspeccionConsultaTest.id(0xe3))
        .param("t4", InspeccionConsultaTest.id(0xe4))
        .param("t5", InspeccionConsultaTest.id(0xe5))
        .param("id", CAMBIOS)
        .param("autor", E1)
        .param("catedratico", C1)
        .update();
    preparar(CAMBIOS, DATOS_COMPLETOS, 4, false);
    mvc.perform(enviar(CAMBIOS).with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ENVIADA"));
    mvc.perform(get(BASE + CAMBIOS + "/historial").with(como(Rol.ESTUDIANTE, E1)))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[4].estado_destino").value("BORRADOR"))
        .andExpect(jsonPath("$[5].estado_destino").value("ENVIADA"));
  }

  @Test
  void ajenoEs404YVisitante401() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    mvc.perform(enviar(BORRADOR).with(como(Rol.ESTUDIANTE, E2))).andExpect(status().isNotFound());
    mvc.perform(enviar(BORRADOR).with(como(Rol.CATEDRATICO, C1))).andExpect(status().isNotFound());
    mvc.perform(enviar(BORRADOR).with(csrf())).andExpect(status().isUnauthorized());
    assertThat(estado(BORRADOR)).isEqualTo("BORRADOR");
  }

  @Test
  void dosEnviosSimultaneosNoDuplicanLaTransicion() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    CountDownLatch listos = new CountDownLatch(1);
    Callable<Integer> operacion =
        () -> {
          autenticar(E1, Rol.ESTUDIANTE);
          listos.await();
          try {
            envio.enviar(
                BORRADOR,
                E1,
                Rol.ESTUDIANTE,
                new EnviarInspeccionDTO(null, null, null),
                null,
                false);
            return 200;
          } catch (NegocioException e) {
            return e.getStatusCode().value();
          } catch (RuntimeException e) {
            return 409;
          } finally {
            SecurityContextHolder.clearContext();
          }
        };
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var una = ejecutor.submit(operacion);
      var otra = ejecutor.submit(operacion);
      listos.countDown();
      assertThat(List.of(una.get(15, TimeUnit.SECONDS), otra.get(15, TimeUnit.SECONDS)))
          .allMatch(codigo -> codigo == 200 || codigo == 409)
          .contains(200);
    }
    assertThat(estado(BORRADOR)).isEqualTo("ENVIADA");
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from transicion_inspeccion
                    where inspeccion_id = :id and estado_origen = 'BORRADOR' and estado_destino = 'ENVIADA'
                    """)
                .param("id", BORRADOR)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void dtoCompletoCanonizaOrdenEscalaYUuidPeroNoIgnoraCamposDelCliente() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    String cuerpo = "{\"indice_condicion\":1.00,\"estado_calculado\":\"REGULAR\"}";
    MvcResult original = conClave(BORRADOR, " canonica ", cuerpo, false, E1, Rol.ESTUDIANTE);
    assertThat(original.getResponse().getStatus()).isEqualTo(200);
    MvcResult replay =
        mvc.perform(
                post(BASE + BORRADOR.toString().toUpperCase(java.util.Locale.ROOT) + "/envio")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(InspeccionController.IDEMPOTENCIA, "canonica")
                    .content("{\"estado_calculado\":\"REGULAR\",\"indice_condicion\":1e0}")
                    .with(como(Rol.ESTUDIANTE, E1)))
            .andReturn();
    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(replay.getResponse().getContentAsByteArray())
        .containsExactly(original.getResponse().getContentAsByteArray());
    List<String> antes = instantanea(BORRADOR);
    exigirError(
        conClave(
            BORRADOR,
            "canonica",
            "{\"indice_condicion\":2,\"estado_calculado\":\"REGULAR\"}",
            false,
            E1,
            Rol.ESTUDIANTE),
        409,
        "clave-idempotencia-incompatible");
    exigirError(
        conClave(
            BORRADOR,
            "canonica",
            "{\"indice_condicion\":1,\"estado_calculado\":\"BUENO\"}",
            false,
            E1,
            Rol.ESTUDIANTE),
        409,
        "clave-idempotencia-incompatible");
    assertThat(instantanea(BORRADOR)).isEqualTo(antes);
  }

  @Test
  void claveUnicode100EsValidaY101NoMuta() throws Exception {
    List<String> antes = instantanea(ENVIADA);
    exigirError(
        conClave(ENVIADA, "😀".repeat(101), "{}", false, E1, Rol.ESTUDIANTE),
        400,
        "idempotencia-invalida");
    assertThat(instantanea(ENVIADA)).isEqualTo(antes);
    assertThat(
            conClave(ENVIADA, "😀".repeat(100), null, false, E1, Rol.ESTUDIANTE)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    MvcResult replay = conClave(ENVIADA, "😀".repeat(100), null, false, E1, Rol.ESTUDIANTE);
    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(instantanea(ENVIADA).subList(0, 4)).isEqualTo(antes.subList(0, 4));
  }

  @Test
  void huellaVinculaRecursoPayloadSyncYCuerpoAusente() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    MvcResult original = conClave(BORRADOR, "huella", "{}", false, E1, Rol.ESTUDIANTE);
    assertThat(original.getResponse().getStatus()).isEqualTo(200);
    List<String> antes = instantanea(BORRADOR);
    for (MvcResult rechazo :
        List.of(
            conClave(
                BORRADOR,
                "huella",
                "{\"enviada_en\":\"2026-10-20T10:00:00Z\"}",
                false,
                E1,
                Rol.ESTUDIANTE),
            conClave(BORRADOR, "huella", "{}", true, E1, Rol.ESTUDIANTE),
            conClave(CAMBIOS, "huella", "{}", false, E1, Rol.ESTUDIANTE),
            conClave(BORRADOR, "huella", null, false, E1, Rol.ESTUDIANTE))) {
      exigirError(rechazo, 409, "clave-idempotencia-incompatible");
    }
    assertThat(instantanea(BORRADOR)).isEqualTo(antes);
  }

  @Test
  void rechazo422SeConservaTrasCorregirDatosHastaUsarNuevaClave() throws Exception {
    preparar(BORRADOR, DATOS_SIN_TRES, 0, false);
    List<String> antes = instantanea(BORRADOR);
    MvcResult rechazo = conClave(BORRADOR, "rechazo", "{}", false, E1, Rol.ESTUDIANTE);
    exigirError(rechazo, 422, "inspeccion-no-publicable");
    assertThat(instantanea(BORRADOR).subList(0, 4)).isEqualTo(antes.subList(0, 4));
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    List<String> corregida = instantanea(BORRADOR);
    MvcResult replay = conClave(BORRADOR, "rechazo", "{}", false, E1, Rol.ESTUDIANTE);
    exigirError(replay, 422, "inspeccion-no-publicable");
    assertThat(replay.getResponse().getContentAsByteArray())
        .containsExactly(rechazo.getResponse().getContentAsByteArray());
    assertThat(replay.getResponse().getContentType()).isEqualTo("application/problem+json");
    assertThat(instantanea(BORRADOR)).isEqualTo(corregida);
    assertThat(
            conClave(BORRADOR, "corregida", "{}", false, E1, Rol.ESTUDIANTE)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  @Test
  void publicadaReproduceBytesDespuesDelCommitSinPublicarDeNuevo() throws Exception {
    preparar(BORRADOR_PRO, DATOS_COMPLETOS, 4, false);
    String cuerpo = "{\"enviada_en\":\"2026-10-20T10:00:00Z\"}";
    MvcResult primera =
        conClave(BORRADOR_PRO, "publicada", cuerpo, false, PROFESIONAL, Rol.PROFESIONAL_EXTERNO);
    assertThat(primera.getResponse().getStatus()).isEqualTo(200);
    assertThat(JsonPath.<String>read(primera.getResponse().getContentAsString(), "$.estado"))
        .isEqualTo("PUBLICADA");
    List<String> antes = instantanea(BORRADOR_PRO);
    MvcResult replay =
        conClave(BORRADOR_PRO, "publicada", cuerpo, false, PROFESIONAL, Rol.PROFESIONAL_EXTERNO);
    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(replay.getResponse().getContentType()).isEqualTo("application/json");
    assertThat(replay.getResponse().getContentAsByteArray())
        .containsExactly(primera.getResponse().getContentAsByteArray());
    assertThat(instantanea(BORRADOR_PRO)).isEqualTo(antes);
  }

  @Test
  void propietarioEliminacionYPermisosSeCompruebanAntesDelReplay() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    assertThat(
            conClave(BORRADOR, "autorizacion", "{}", false, E1, Rol.ESTUDIANTE)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    List<String> antes = instantanea(BORRADOR);
    exigirError(
        conClave(BORRADOR, "autorizacion", "{}", false, E1, Rol.ADMINISTRADOR),
        403,
        "acceso-denegado");
    assertThat(instantanea(BORRADOR)).isEqualTo(antes);
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", E2)
        .param("id", BORRADOR)
        .update();
    antes = instantanea(BORRADOR);
    exigirError(
        conClave(BORRADOR, "autorizacion", "{}", false, E1, Rol.ESTUDIANTE),
        404,
        "inspeccion-no-encontrada");
    assertThat(instantanea(BORRADOR)).isEqualTo(antes);
    jdbc.sql("update inspeccion set autor_id = :autor, eliminado_en = now() where id = :id")
        .param("autor", E1)
        .param("id", BORRADOR)
        .update();
    antes = instantanea(BORRADOR);
    exigirError(
        conClave(BORRADOR, "autorizacion", "{}", false, E1, Rol.ESTUDIANTE),
        404,
        "inspeccion-no-encontrada");
    assertThat(instantanea(BORRADOR)).isEqualTo(antes);
  }

  @Test
  void estadoBindingYAutenticacionNoGuardanResultados() throws Exception {
    List<String> antes = instantanea(BORRADOR);
    exigirError(
        conClave(
            InspeccionConsultaTest.PUBLICADA_RECIENTE, "estado", "{}", false, E1, Rol.ESTUDIANTE),
        409,
        "inspeccion-no-editable");
    assertThat(
            conClave(BORRADOR, "binding", "{", false, E1, Rol.ESTUDIANTE).getResponse().getStatus())
        .isEqualTo(400);
    assertThat(
            mvc.perform(enviar(BORRADOR).header(InspeccionController.IDEMPOTENCIA, "anonimo"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    assertThat(instantanea(BORRADOR)).isEqualTo(antes);
  }

  @Test
  void claveBlancaYReintentoEnviadaConservanComportamientoAnterior() throws Exception {
    preparar(BORRADOR, DATOS_COMPLETOS, 4, false);
    assertThat(conClave(BORRADOR, "   ", "{}", false, E1, Rol.ESTUDIANTE).getResponse().getStatus())
        .isEqualTo(200);
    List<String> antes = instantanea(BORRADOR);
    assertThat(conClave(BORRADOR, "   ", "{}", false, E1, Rol.ESTUDIANTE).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(instantanea(BORRADOR)).isEqualTo(antes);
    assertThat(
            conClave(BORRADOR, "ya-enviada", "{}", false, E1, Rol.ESTUDIANTE)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(instantanea(BORRADOR).subList(0, 4)).isEqualTo(antes.subList(0, 4));
  }

  private MvcResult conClave(
      UUID id, String clave, String cuerpo, boolean sync, UUID actor, Rol rol) throws Exception {
    var peticion =
        post(BASE + id + "/envio")
            .contentType(MediaType.APPLICATION_JSON)
            .header(InspeccionController.IDEMPOTENCIA, clave)
            .header(InspeccionController.SYNC, sync);
    if (cuerpo != null) {
      peticion.content(cuerpo);
    }
    return mvc.perform(peticion.with(como(rol, actor))).andReturn();
  }

  private static void exigirError(MvcResult respuesta, int estado, String tipo) throws Exception {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(estado);
    assertThat(JsonPath.<String>read(respuesta.getResponse().getContentAsString(), "$.type"))
        .isEqualTo("https://sgp.cunoc.usac.edu.gt/errores/" + tipo);
  }

  private List<String> instantanea(UUID id) {
    return jdbc.sql(
            """
        select row_to_json(i)::text,
          (select count(*)::text from transicion_inspeccion),
          (select count(*)::text from notificacion),
          (select count(*)::text from auditoria),
          (select count(*)::text from idempotencia),
          (select row_to_json(p)::text from puente p where p.id = i.puente_id),
          (select count(*)::text from orden_mantenimiento)
        from inspeccion i where id = :id
        """)
        .param("id", id)
        .query(
            (rs, n) ->
                List.of(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getString(7)))
        .single();
  }

  private void preparar(UUID inspeccion, String datos, int vigentes, boolean extraPurgada) {
    jdbc.sql(
            """
            update inspeccion
            set datos = CAST(:datos AS jsonb),
                datos_permanentes_confirmados_en = :conf,
                fecha_inspeccion = DATE '2026-10-20'
            where id = :id
            """)
        .param("datos", datos)
        .param("conf", Timestamp.from(Instant.parse("2026-10-20T12:00:00Z")))
        .param("id", inspeccion)
        .update();
    fotos(inspeccion, vigentes, extraPurgada);
  }

  private List<UUID> fotos(UUID inspeccion, int vigentes, boolean extraPurgada) {
    jdbc.sql("delete from archivo where inspeccion_id = :id").param("id", inspeccion).update();
    java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
    for (int i = 0; i < vigentes; i++) {
      UUID id = UUID.randomUUID();
      ids.add(id);
      insertarFoto(id, inspeccion, false);
    }
    if (extraPurgada) {
      insertarFoto(UUID.randomUUID(), inspeccion, true);
    }
    return ids;
  }

  private void insertarFoto(UUID id, UUID inspeccion, boolean purgada) {
    jdbc.sql(
            """
            insert into archivo (id, inspeccion_id, tipo, clave, clave_miniatura, tamano, purgado_en)
            values (:id, :ins, 'WEBP', :clave, :mini, 10, :purgado)
            """)
        .param("id", id)
        .param("ins", inspeccion)
        .param("clave", "envio/" + inspeccion + "/" + id + ".webp")
        .param("mini", "envio/" + inspeccion + "/" + id + "-mini.webp")
        .param("purgado", purgada ? Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")) : null)
        .update();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder enviar(
      UUID id) {
    return post(BASE + id + "/envio")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}")
        .with(csrf());
  }

  private String estado(UUID id) {
    return jdbc.sql("select estado from inspeccion where id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private static void autenticar(UUID usuario, Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }
}
