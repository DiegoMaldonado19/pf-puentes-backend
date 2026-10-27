package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import com.jayway.jsonpath.JsonPath;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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
@Sql("/inspeccion/consulta.sql")
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionAltaIdempotenciaTest extends PruebaIntegracion {
  private static final String BASE = "/api/v1/inspecciones";
  private static final UUID AUTOR = InspeccionConsultaTest.C1;
  private static final UUID ID = InspeccionConsultaTest.id(0xc01);
  private static final UUID PUENTE = InspeccionInicioTest.LAS_ROSAS;
  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager gestor;

  @Test
  void replayTrasEdicionNoReconstruyeDetalleNiRepiteEfectos() throws Exception {
    String cuerpo = cuerpo(ID);
    MvcResult primera = alta(" alta ", cuerpo);
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    assertThat(
            mvc.perform(
                    put(BASE + "/" + ID)
                        .with(como(Rol.CATEDRATICO, AUTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"datos\":{},\"version\":0,\"fecha_inspeccion\":\"1999-01-01\"}"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    List<String> antes = instantanea();
    igual(alta("alta", cuerpo), primera);
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(contar("transicion_inspeccion", "inspeccion_id", ID)).isEqualTo(1);
    assertThat(contar("idempotencia", "usuario_id", AUTOR)).isEqualTo(1);
  }

  @Test
  void huellaIncluyeTodosLosCamposInclusoLosIgnoradosPorDominio() throws Exception {
    String cuerpo = cuerpo(ID);
    MvcResult primera = alta("huella", cuerpo);
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    List<String> antes = instantanea();
    for (String otro :
        List.of(
            cuerpo.replace(ID.toString(), InspeccionConsultaTest.id(0xc02).toString()),
            cuerpo.replace(PUENTE.toString(), InspeccionInicioTest.EL_SAUCE.toString()),
            cuerpo.replace("tablet", "otro"),
            cuerpo.replace(
                "\"dispositivo_id\"",
                "\"curso_id\":\"" + InspeccionConsultaTest.id(0x31) + "\",\"dispositivo_id\""),
            cuerpo.replace(
                "\"dispositivo_id\"",
                "\"creada_en_dispositivo\":\"2026-01-01T00:00:00Z\",\"dispositivo_id\""),
            cuerpo.replace(
                "\"dispositivo_id\"",
                "\"asignacion_id\":\""
                    + InspeccionInicioTest.ASIGNACION_ROSAS
                    + "\",\"dispositivo_id\""),
            cuerpo.replace(
                "\"dispositivo_id\"", "\"latitud\":14,\"longitud\":-91,\"dispositivo_id\""),
            cuerpo.replace(LocalDate.now(ValidacionFechaInspeccion.ZONA).toString(), "1999-01-01"),
            correccion(cuerpo))) {
      error(
          alta("huella", otro),
          409,
          "clave-idempotencia-incompatible",
          "Clave de idempotencia incompatible",
          "La clave ya está asociada a otra huella o a un resultado heredado no verificable.");
      assertThat(instantanea()).isEqualTo(antes);
    }
  }

  @Test
  void ordenUuidYNumerosEquivalentesConservanBytes() throws Exception {
    String cuerpo =
        cuerpo(ID)
            .replace(
                "\"dispositivo_id\"", "\"latitud\":14.0,\"longitud\":-91.0,\"dispositivo_id\"");
    MvcResult primera = alta("canonica", cuerpo);
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    List<String> antes = instantanea();
    igual(
        alta(
            "canonica",
            cuerpo
                .replace(ID.toString(), ID.toString().toUpperCase(java.util.Locale.ROOT))
                .replace("14.0", "14e0")
                .replace("-91.0", "-91.000")),
        primera);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void correccionConservaReglasYReplayNoExigeNuevaEditabilidad() throws Exception {
    String cuerpo = correccion(cuerpo(ID));
    MvcResult primera = alta("correccion", cuerpo);
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    // Simula una transición posterior: el replay no intenta crear otra corrección viva.
    jdbc.sql(
            "update inspeccion set estado = 'PUBLICADA', publicada_en = now(), indice_condicion = 80, estado_calculado = 'BUENO', estado_confirmado = 'BUENO' where id = :id")
        .param("id", ID)
        .update();
    List<String> antes = instantanea();
    igual(alta("correccion", cuerpo), primera);
    assertThat(instantanea()).isEqualTo(antes);
    error(
        alta("correccion", cuerpo.replace("\"motivo\"", "\"otro motivo\"")),
        409,
        "clave-idempotencia-incompatible",
        "Clave de idempotencia incompatible",
        "La clave ya está asociada a otra huella o a un resultado heredado no verificable.");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void autorizacionActualDeniegaAutorAjenoYEliminadaAntesDeReplay() throws Exception {
    assertThat(alta("autorizacion", cuerpo(ID)).getResponse().getStatus()).isEqualTo(201);
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", InspeccionConsultaTest.E2)
        .param("id", ID)
        .update();
    List<String> antes = instantanea();
    conflictoId(alta("autorizacion", cuerpo(ID)));
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", AUTOR)
        .param("id", ID)
        .update();
    assertThat(
            mvc.perform(delete(BASE + "/" + ID).with(como(Rol.CATEDRATICO, AUTOR)))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(204);
    antes = instantanea();
    conflictoId(alta("autorizacion", cuerpo(ID)));
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void asignacionVencidaDeniegaReplaySinGuardarOtroResultado() throws Exception {
    UUID estudiante = InspeccionConsultaTest.E1;
    assertThat(
            peticion("estudiante", cuerpo(ID), estudiante, Rol.ESTUDIANTE)
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    jdbc.sql("update curso set vigente = false where id = :id")
        .param("id", InspeccionConsultaTest.id(0x31))
        .update();
    List<String> antes = instantanea();
    error(
        peticion("estudiante", cuerpo(ID), estudiante, Rol.ESTUDIANTE),
        403,
        "puente-no-asignado",
        "Puente no asignado",
        "No tiene una asignación vigente para inspeccionar ese puente.");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void uuidLegadoDeduplicaSinReescribirYClaveBlancaNoReserva() throws Exception {
    MvcResult legado = alta(null, cuerpo(ID));
    assertThat(legado.getResponse().getStatus()).isEqualTo(201);
    List<String> antes = instantanea();
    igual(alta("   ", cuerpo(ID)), legado);
    assertThat(instantanea()).isEqualTo(antes);
    igual(alta("legado", cuerpo(ID)), legado);
    assertThat(contar("transicion_inspeccion", "inspeccion_id", ID)).isEqualTo(1);
    assertThat(contar("idempotencia", "usuario_id", AUTOR)).isEqualTo(1);
    List<String> guardada = instantanea();
    igual(alta("legado", cuerpo(ID)), legado);
    assertThat(instantanea()).isEqualTo(guardada);
  }

  @Test
  void legadoJsonbNoSeConvierteNiEjecuta() throws Exception {
    jdbc.sql(
            "insert into idempotencia (id, usuario_id, clave, estado_http, respuesta) values (:id, :usuario, 'jsonb', 201, '{}'::jsonb)")
        .param("id", UUID.randomUUID())
        .param("usuario", AUTOR)
        .update();
    List<String> antes = instantanea();
    error(
        alta("jsonb", cuerpo(ID)),
        409,
        "clave-idempotencia-incompatible",
        "Clave de idempotencia incompatible",
        "La clave ya está asociada a otra huella o a un resultado heredado no verificable.");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void falloRealPostgresqlRevierteAltaTransicionAuditoriaYResultado() throws Exception {
    for (boolean corrige : List.of(false, true)) {
      UUID id = corrige ? InspeccionConsultaTest.id(0xc02) : ID;
      String cuerpo = corrige ? correccion(cuerpo(id)) : cuerpo(id);
      String clave = "fallo-alta" + corrige;
      List<String> antes = instantanea();
      jdbc.sql(
              "alter table idempotencia add constraint prueba_alta_resultado check (clave <> '"
                  + clave
                  + "')")
          .update();
      try {
        error(
            alta(clave, cuerpo),
            500,
            null,
            "Internal Server Error",
            "Ocurrió un error inesperado.");
      } finally {
        jdbc.sql("alter table idempotencia drop constraint prueba_alta_resultado").update();
      }
      assertThat(instantanea()).isEqualTo(antes);
      assertThat(alta(clave, cuerpo).getResponse().getStatus()).isEqualTo(201);
      assertThat(contar("transicion_inspeccion", "inspeccion_id", id)).isEqualTo(1);
      assertThat(contar("auditoria", "entidad_id", id)).isEqualTo(1);
    }
  }

  @Test
  void duplicadosHttpConcurrentesProducenUnaAltaYResultadoExacto() throws Exception {
    CountDownLatch inicio = new CountDownLatch(1);
    Callable<MvcResult> operacion =
        () -> {
          inicio.await();
          return alta("concurrente", cuerpo(ID));
        };
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var una = ejecutor.submit(operacion);
      var otra = ejecutor.submit(operacion);
      inicio.countDown();
      MvcResult primera = una.get(15, TimeUnit.SECONDS);
      assertThat(primera.getResponse().getStatus()).isEqualTo(201);
      igual(otra.get(15, TimeUnit.SECONDS), primera);
    }
    assertThat(contar("inspeccion", "id", ID)).isEqualTo(1);
    assertThat(contar("transicion_inspeccion", "inspeccion_id", ID)).isEqualTo(1);
    assertThat(contar("auditoria", "entidad_id", ID)).isEqualTo(1);
    assertThat(contar("idempotencia", "usuario_id", AUTOR)).isEqualTo(1);
  }

  @Test
  void esperaDosSegundosNoCacheaConflictoYPermiteReintentar() throws Exception {
    String clave = "espera-alta";
    byte[] bytes = clave.getBytes(StandardCharsets.UTF_8);
    long bloqueo =
        ByteBuffer.wrap(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        ByteBuffer.allocate(20 + bytes.length)
                            .putLong(AUTOR.getMostSignificantBits())
                            .putLong(AUTOR.getLeastSignificantBits())
                            .putInt(bytes.length)
                            .put(bytes)
                            .array()))
            .getLong();
    List<String> antes = instantanea();
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      new TransactionTemplate(gestor)
          .executeWithoutResult(
              tx -> {
                jdbc.sql("select pg_advisory_xact_lock(:id)")
                    .param("id", bloqueo)
                    .query(Object.class)
                    .single();
                var pendiente = ejecutor.submit(() -> alta(clave, cuerpo(ID)));
                try {
                  error(
                      pendiente.get(5, TimeUnit.SECONDS),
                      409,
                      "operacion-en-proceso",
                      "Operación en proceso",
                      "La operación sigue en curso; se puede reintentar con la misma clave.");
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
    }
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(alta(clave, cuerpo(ID)).getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  void claveUnicodeLimiteYRechazosNoCacheables() throws Exception {
    List<String> antes = instantanea();
    error(
        alta("😀".repeat(101), cuerpo(ID)),
        400,
        "idempotencia-invalida",
        "Clave de idempotencia inválida",
        "Idempotency-Key no puede superar 100 caracteres.");
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(
            alta("invalido", cuerpo(ID).replace(ID.toString(), UUID.randomUUID().toString()))
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(peticion("rol", cuerpo(ID), AUTOR, Rol.ADMINISTRADOR).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(instantanea()).isEqualTo(antes);
    MvcResult primera = alta("😀".repeat(100), cuerpo(ID));
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    antes = instantanea();
    igual(alta("😀".repeat(100), cuerpo(ID)), primera);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void erroresDeNegocioNoSeGuardanYConservanReglasDeCorreccion() throws Exception {
    List<String> antes = instantanea();
    error(
        alta(
            "sin-formulario",
            cuerpo(ID).replace(PUENTE.toString(), InspeccionInicioTest.BAJA.toString())),
        422,
        "puente-inactivo",
        "El puente está inactivo",
        "Un puente dado de baja no se puede inspeccionar.");
    assertThat(instantanea()).isEqualTo(antes);
    error(
        alta(
            "correccion-viva",
            correccion(cuerpo(ID))
                .replace(
                    InspeccionConsultaTest.PUBLICADA_PRO.toString(),
                    InspeccionConsultaTest.PUBLICADA_RECIENTE.toString())),
        409,
        "correccion-en-curso",
        "Ya hay una corrección en curso",
        "Esa inspección ya tiene una corrección viva.");
    assertThat(instantanea()).isEqualTo(antes);
    error(
        alta(
            "no-corregible",
            correccion(cuerpo(ID))
                .replace(
                    InspeccionConsultaTest.PUBLICADA_PRO.toString(),
                    InspeccionConsultaTest.BORRADOR_E1.toString())),
        422,
        "inspeccion-no-corregible",
        "La inspección no se puede corregir",
        "Solo se corrige una inspección publicada del mismo puente.");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void putYEnvioRechazanClaveDeAltaSinCambiarSuContrato() throws Exception {
    assertThat(alta("compartida", cuerpo(ID)).getResponse().getStatus()).isEqualTo(201);
    List<String> antes = instantanea();
    for (MvcResult respuesta :
        List.of(
            mvc.perform(
                    put(BASE + "/" + ID)
                        .with(como(Rol.CATEDRATICO, AUTOR))
                        .header("Idempotency-Key", "compartida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datos\":{},\"version\":0}"))
                .andReturn(),
            mvc.perform(
                    post(BASE + "/" + ID + "/envio")
                        .with(como(Rol.CATEDRATICO, AUTOR))
                        .header("Idempotency-Key", "compartida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn())) {
      assertThat(respuesta.getResponse().getStatus()).isEqualTo(409);
      assertThat(respuesta.getResponse().getContentAsString())
          .contains("clave-idempotencia-incompatible");
    }
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void correccionEliminadaNoReproduceNiAfectaLaSiguienteCorreccion() throws Exception {
    String cuerpo = correccion(cuerpo(ID));
    assertThat(alta("anterior", cuerpo).getResponse().getStatus()).isEqualTo(201);
    assertThat(
            mvc.perform(delete(BASE + "/" + ID).with(como(Rol.CATEDRATICO, AUTOR)))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(204);
    assertThat(
            alta("siguiente", correccion(cuerpo(InspeccionConsultaTest.id(0xc02))))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    List<String> antes = instantanea();
    conflictoId(alta("anterior", cuerpo));
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void replayNoNecesitaFormularioActivoYUsuariosNoCompartenResultados() throws Exception {
    MvcResult primera = alta("aislada", cuerpo(ID));
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    UUID profesional = InspeccionConsultaTest.PROFESIONAL;
    MvcResult otra =
        peticion(
            "aislada",
            cuerpo(InspeccionConsultaTest.id(0xc02)),
            profesional,
            Rol.PROFESIONAL_EXTERNO);
    assertThat(otra.getResponse().getStatus()).isEqualTo(201);
    assertThat(contar("idempotencia", "usuario_id", profesional)).isEqualTo(1);
    jdbc.sql("update formulario_version set estado = 'RETIRADA' where estado = 'ACTIVA'").update();
    List<String> antes = instantanea();
    igual(alta("aislada", cuerpo(ID)), primera);
    assertThat(instantanea()).isEqualTo(antes);
    conflictoId(peticion("aislada", cuerpo(ID), profesional, Rol.PROFESIONAL_EXTERNO));
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(
            mvc.perform(
                    post(BASE)
                        .header("Idempotency-Key", "anonima")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ID)))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    assertThat(instantanea()).isEqualTo(antes);
  }

  private MvcResult alta(String clave, String cuerpo) throws Exception {
    return peticion(clave, cuerpo, AUTOR, Rol.CATEDRATICO);
  }

  private MvcResult peticion(String clave, String cuerpo, UUID actor, Rol rol) throws Exception {
    var peticion =
        post(BASE).with(como(rol, actor)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    if (clave != null) peticion.header("Idempotency-Key", clave);
    return mvc.perform(peticion).andReturn();
  }

  private static String cuerpo(UUID id) {
    return "{\"id\":\"%s\",\"puente_id\":\"%s\",\"fecha_inspeccion\":\"%s\",\"dispositivo_id\":\"tablet\"}"
        .formatted(id, PUENTE, LocalDate.now(ValidacionFechaInspeccion.ZONA));
  }

  private static String correccion(String cuerpo) {
    return cuerpo.replace(
        "\"dispositivo_id\"",
        "\"supersede_a_id\":\""
            + InspeccionConsultaTest.PUBLICADA_PRO
            + "\",\"motivo_correccion\":\"motivo\",\"dispositivo_id\"");
  }

  private static void igual(MvcResult actual, MvcResult esperado) throws Exception {
    assertThat(actual.getResponse().getStatus()).isEqualTo(esperado.getResponse().getStatus());
    assertThat(actual.getResponse().getContentType())
        .isEqualTo(esperado.getResponse().getContentType());
    assertThat(actual.getResponse().getContentAsByteArray())
        .containsExactly(esperado.getResponse().getContentAsByteArray());
  }

  private static void conflictoId(MvcResult respuesta) throws Exception {
    error(
        respuesta,
        409,
        "inspeccion-en-conflicto",
        "No se puede crear la inspección",
        "El identificador ya está en uso.");
  }

  private static void error(
      MvcResult respuesta, int estado, String tipo, String titulo, String detalle)
      throws Exception {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(estado);
    String uri = tipo == null ? "about:blank" : "https://sgp.cunoc.usac.edu.gt/errores/" + tipo;
    assertThat(respuesta.getResponse().getContentType()).isEqualTo("application/problem+json");
    var json = JsonPath.parse(respuesta.getResponse().getContentAsString());
    if (tipo == null) {
      assertThat(respuesta.getResponse().getContentAsString()).doesNotContain("\"type\"");
    } else {
      assertThat(json.<String>read("$.type")).isEqualTo(uri);
    }
    assertThat(json.<String>read("$.title")).isEqualTo(titulo);
    assertThat(json.<Integer>read("$.status")).isEqualTo(estado);
    assertThat(json.<String>read("$.detail")).isEqualTo(detalle);
    assertThat(json.<String>read("$.instance")).isEqualTo(BASE);
  }

  private long contar(String tabla, String columna, UUID id) {
    return jdbc.sql("select count(*) from " + tabla + " where " + columna + " = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private List<String> instantanea() {
    return jdbc.sql(
            """
        select
          (select coalesce(jsonb_agg(to_jsonb(i) order by id)::text, '[]') from inspeccion i),
          (select count(*)::text from transicion_inspeccion),
          (select count(*)::text from auditoria),
          (select count(*)::text from idempotencia),
          (select count(*)::text from copia_conflicto),
          (select count(*)::text from notificacion)
        """)
        .query(
            (rs, n) ->
                List.of(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6)))
        .single();
  }
}
