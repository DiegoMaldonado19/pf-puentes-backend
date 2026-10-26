package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import com.cunoc.puentes.sync.IdempotenciaService;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import com.cunoc.puentes.usuario.Rol;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
class IdempotenciaCompatibilidadTest extends PruebaIntegracion {

  private static final String TIPO_INCOMPATIBLE =
      "https://sgp.cunoc.usac.edu.gt/errores/clave-idempotencia-incompatible";
  private static final String BASE = "/api/v1/inspecciones/";
  private static final UUID E1 = InspeccionConsultaTest.E1;
  private static final UUID BORRADOR = InspeccionConsultaTest.BORRADOR_E1;
  private static final String DATOS_COMPLETOS = InspeccionEnvioTest.DATOS_COMPLETOS;
  private static final String CUERPO_BRUTO = " \n{resultado genérico P6}\t ";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired IdempotenciaService idempotencia;

  @Test
  void guardarRechazaResultadoGenericoSinMutarElBorradorNiElResultadoOriginal() throws Exception {
    ResultadoOriginal original = guardarResultadoGenerico("p6-put");
    InspeccionAntes antes = inspeccionAntes(BORRADOR);
    Contadores antesContadores = contadores();

    MvcResult respuesta = guardarConClave(original.clave(), antes.version());
    RespuestaIdempotente replay = reproducirResultadoOriginal(original);

    SoftAssertions softly = new SoftAssertions();
    afirmarConflicto(softly, respuesta);
    softly.assertThat(inspeccionAntes(BORRADOR)).isEqualTo(antes);
    softly.assertThat(contadores()).isEqualTo(antesContadores);
    afirmarReplayOriginal(softly, original, replay);
    softly.assertAll();
  }

  @Test
  void enviarRechazaResultadoGenericoSinEnviarNiAlterarElResultadoOriginal() throws Exception {
    prepararParaEnvio(BORRADOR);
    ResultadoOriginal original = guardarResultadoGenerico("p6-post");
    InspeccionAntes antes = inspeccionAntes(BORRADOR);
    Contadores antesContadores = contadores();

    MvcResult respuesta = enviarConClave(original.clave());
    RespuestaIdempotente replay = reproducirResultadoOriginal(original);

    SoftAssertions softly = new SoftAssertions();
    afirmarConflicto(softly, respuesta);
    softly.assertThat(inspeccionAntes(BORRADOR)).isEqualTo(antes);
    softly.assertThat(contadores()).isEqualTo(antesContadores);
    afirmarReplayOriginal(softly, original, replay);
    softly.assertAll();
  }

  @ParameterizedTest
  @MethodSource("filasHeredadasNoP5")
  void guardarYEnviarRechazanFilasHeredadasNoReproducibles(
      String operacion, String clave, String respuestaJson) throws Exception {
    if (operacion.equals("ENVIAR")) {
      prepararParaEnvio(BORRADOR);
    }
    insertarFilaHeredada(clave, respuestaJson);
    InspeccionAntes antes = inspeccionAntes(BORRADOR);
    Contadores antesContadores = contadores();
    String respuestaGuardada = respuestaJsonb(clave);

    MvcResult respuesta =
        operacion.equals("GUARDAR")
            ? guardarConClave(clave, antes.version())
            : enviarConClave(clave);

    SoftAssertions softly = new SoftAssertions();
    afirmarConflicto(softly, respuesta);
    softly.assertThat(inspeccionAntes(BORRADOR)).isEqualTo(antes);
    softly.assertThat(contadores()).isEqualTo(antesContadores);
    softly.assertThat(respuestaJsonb(clave)).isEqualTo(respuestaGuardada);
    softly.assertAll();
  }

  @Test
  void envioRechazaInclusoUnDtoP5HeredadoValidoSinConvertirNiEjecutar() throws Exception {
    String dto =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        BASE + BORRADOR)
                    .with(como(Rol.ESTUDIANTE, E1)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    insertarFilaHeredada("dto-p5", dto);
    InspeccionAntes antes = inspeccionAntes(BORRADOR);
    Contadores cantidades = contadores();
    String legado = respuestaJsonb("dto-p5");
    MvcResult respuesta = enviarConClave("dto-p5");
    SoftAssertions softly = new SoftAssertions();
    afirmarConflicto(softly, respuesta);
    softly.assertThat(inspeccionAntes(BORRADOR)).isEqualTo(antes);
    softly.assertThat(contadores()).isEqualTo(cantidades);
    softly.assertThat(respuestaJsonb("dto-p5")).isEqualTo(legado);
    softly.assertAll();
  }

  private static Stream<Arguments> filasHeredadasNoP5() {
    return Stream.of(
        arguments("GUARDAR", "legado-put-null", null),
        arguments("GUARDAR", "legado-put-escalar", "\"respuesta heredada\""),
        arguments("GUARDAR", "legado-put-objeto", "{\"campo_ajeno\":true}"),
        arguments("ENVIAR", "legado-post-null", null),
        arguments("ENVIAR", "legado-post-escalar", "\"respuesta heredada\""),
        arguments("ENVIAR", "legado-post-objeto", "{\"campo_ajeno\":true}"));
  }

  private ResultadoOriginal guardarResultadoGenerico(String clave) {
    HuellaIdempotencia huella = new HuellaIdempotencia(1, new byte[32]);
    AtomicInteger callbacks = new AtomicInteger();
    byte[] cuerpo = CUERPO_BRUTO.getBytes(StandardCharsets.UTF_8);
    RespuestaIdempotente guardada =
        idempotencia.ejecutar(
            E1,
            clave,
            huella,
            Duration.ofSeconds(1),
            () -> {
              callbacks.incrementAndGet();
              return new RespuestaIdempotente(207, cuerpo);
            });
    assertThat(guardada.estadoHttp()).isEqualTo(207);
    return new ResultadoOriginal(clave, huella, cuerpo, callbacks);
  }

  private RespuestaIdempotente reproducirResultadoOriginal(ResultadoOriginal original) {
    return idempotencia.ejecutar(
        E1,
        original.clave(),
        original.huella(),
        Duration.ofSeconds(1),
        () -> {
          original.callbacks().incrementAndGet();
          return new RespuestaIdempotente(500, new byte[] {99});
        });
  }

  private static void afirmarReplayOriginal(
      SoftAssertions softly, ResultadoOriginal original, RespuestaIdempotente replay) {
    softly.assertThat(replay.estadoHttp()).isEqualTo(207);
    softly.assertThat(replay.cuerpo()).containsExactly(original.cuerpo());
    softly.assertThat(original.callbacks()).hasValue(1);
  }

  private void insertarFilaHeredada(String clave, String respuestaJson) {
    if (respuestaJson == null) {
      jdbc.sql(
              """
              insert into idempotencia (id, usuario_id, clave, estado_http, respuesta)
              values (:id, :usuario, :clave, 200, null)
              """)
          .param("id", UUID.randomUUID())
          .param("usuario", E1)
          .param("clave", clave)
          .update();
      return;
    }
    jdbc.sql(
            """
            insert into idempotencia (id, usuario_id, clave, estado_http, respuesta)
            values (:id, :usuario, :clave, 200, cast(:respuesta as jsonb))
            """)
        .param("id", UUID.randomUUID())
        .param("usuario", E1)
        .param("clave", clave)
        .param("respuesta", respuestaJson)
        .update();
  }

  private MvcResult guardarConClave(String clave, long version) throws Exception {
    return mvc.perform(
            put(BASE + BORRADOR)
                .contentType(MediaType.APPLICATION_JSON)
                .header(InspeccionController.IDEMPOTENCIA, clave)
                .content(
                    "{\"datos\":{\"identificacion\":{\"tipologia\":\"modificada\"}},\"version\":"
                        + version
                        + "}")
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andReturn();
  }

  private MvcResult enviarConClave(String clave) throws Exception {
    return mvc.perform(
            post(BASE + BORRADOR + "/envio")
                .contentType(MediaType.APPLICATION_JSON)
                .header(InspeccionController.IDEMPOTENCIA, clave)
                .content("{}")
                .with(csrf())
                .with(como(Rol.ESTUDIANTE, E1)))
        .andReturn();
  }

  private void prepararParaEnvio(UUID inspeccion) {
    jdbc.sql(
            """
            update inspeccion
            set datos = cast(:datos as jsonb),
                datos_permanentes_confirmados_en = :confirmado,
                fecha_inspeccion = date '2026-10-20'
            where id = :id
            """)
        .param("datos", DATOS_COMPLETOS)
        .param("confirmado", Timestamp.from(Instant.parse("2026-10-20T12:00:00Z")))
        .param("id", inspeccion)
        .update();
    jdbc.sql("delete from archivo where inspeccion_id = :id").param("id", inspeccion).update();
    for (int indice = 0; indice < 4; indice++) {
      UUID archivoId = UUID.randomUUID();
      jdbc.sql(
              """
              insert into archivo
                (id, inspeccion_id, tipo, clave, clave_miniatura, tamano, purgado_en)
              values
                (:id, :inspeccion, 'WEBP', :clave, :miniatura, 10, null)
              """)
          .param("id", archivoId)
          .param("inspeccion", inspeccion)
          .param("clave", "envio/" + inspeccion + "/" + archivoId + ".webp")
          .param("miniatura", "envio/" + inspeccion + "/" + archivoId + "-mini.webp")
          .update();
    }
  }

  private InspeccionAntes inspeccionAntes(UUID id) {
    return jdbc.sql(
            """
            select datos::text, version, estado, ultima_actividad_en::text, sincronizada_en::text
            from inspeccion where id = :id
            """)
        .param("id", id)
        .query(
            (rs, fila) ->
                new InspeccionAntes(
                    rs.getString(1),
                    rs.getLong(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5)))
        .single();
  }

  private Contadores contadores() {
    return jdbc.sql(
            """
            select (select count(*) from transicion_inspeccion),
                   (select count(*) from copia_conflicto),
                   (select count(*) from archivo),
                   (select count(*) from usuario),
                   (select count(*) from notificacion),
                   (select count(*) from auditoria),
                   (select count(*) from idempotencia)
            """)
        .query(
            (rs, fila) ->
                new Contadores(
                    rs.getLong(1),
                    rs.getLong(2),
                    rs.getLong(3),
                    rs.getLong(4),
                    rs.getLong(5),
                    rs.getLong(6),
                    rs.getLong(7)))
        .single();
  }

  private String respuestaJsonb(String clave) {
    return jdbc.sql(
            "select respuesta::text from idempotencia where usuario_id = :usuario and clave = :clave")
        .param("usuario", E1)
        .param("clave", clave)
        .query(String.class)
        .optional()
        .orElse(null);
  }

  private static void afirmarConflicto(SoftAssertions softly, MvcResult respuesta)
      throws Exception {
    softly.assertThat(respuesta.getResponse().getStatus()).isEqualTo(409);
    String tipo = null;
    try {
      tipo = JsonPath.read(respuesta.getResponse().getContentAsString(), "$.type");
    } catch (PathNotFoundException error) {
      // Los handlers anteriores devolvían 500 sin el tipo de conflicto esperado.
    }
    softly.assertThat(tipo).isEqualTo(TIPO_INCOMPATIBLE);
  }

  private record ResultadoOriginal(
      String clave, HuellaIdempotencia huella, byte[] cuerpo, AtomicInteger callbacks) {}

  private record InspeccionAntes(
      String datos, long version, String estado, String actividad, String sincronizada) {}

  private record Contadores(
      long transiciones,
      long conflictos,
      long archivos,
      long usuarios,
      long notificaciones,
      long auditorias,
      long resultados) {}
}
