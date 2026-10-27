package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import com.jayway.jsonpath.JsonPath;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** M08: toda operación bajo prueba entra por HTTP; SQL solo prepara y observa PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/inspeccion/consulta.sql", "/inspeccion/borrador.sql"})
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InspeccionBajaIdempotenciaHttpTest extends PruebaIntegracion {
  static final String BASE = "/api/v1/inspecciones/";
  static final UUID ID = InspeccionConsultaTest.BORRADOR_E1;
  static final UUID AUTOR = InspeccionConsultaTest.E1;
  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager gestor;
  @Autowired jakarta.persistence.EntityManager entidades;

  @Test
  void replay204ExactoNoRepiteBajaVersionActividadAuditoriaNiRelaciones() throws Exception {
    long version = version();
    String relaciones = relaciones();
    vacio(baja(ID, " exacta "));
    assertThat(version()).isEqualTo(version + 1);
    assertThat(relaciones()).isEqualTo(relaciones);
    assertThat(auditorias()).isEqualTo(1);
    assertThat(resultados("exacta")).isEqualTo(1);
    List<String> antes = instantanea();
    vacio(baja(ID, "exacta"));
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(
            mvc.perform(get(BASE + ID).with(como(Rol.ESTUDIANTE, AUTOR)))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ENVIADA", "CAMBIOS_SOLICITADOS"})
  void replayTardioNoExigeEditabilidadNiRestauraEstado(String estado) throws Exception {
    vacio(baja(ID, "tardia"));
    jdbc.sql("update inspeccion set estado = :estado, version = version + 1 where id = :id")
        .param("estado", estado)
        .param("id", ID)
        .update();
    List<String> antes = instantanea();
    vacio(baja(ID, "tardia"));
    assertThat(instantanea()).isEqualTo(antes);
    problema(baja(ID, "otra"), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void autorizacionActualSobreOriginalEliminadoNoSeSustituyePorConocerClave() throws Exception {
    vacio(baja(ID, "permiso"));
    List<String> antes = instantanea();
    assertThat(baja(ID, "permiso", AUTOR, Rol.ADMINISTRADOR).getResponse().getStatus())
        .isEqualTo(403);
    problema(
        baja(ID, "permiso", InspeccionConsultaTest.E2, Rol.ESTUDIANTE),
        404,
        "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", InspeccionConsultaTest.E2)
        .param("id", ID)
        .update();
    antes = instantanea();
    problema(baja(ID, "permiso"), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(resultados("permiso")).isEqualTo(1);
  }

  @Test
  void claveDeOtroRecursoPropioNoEliminaNiReproduceResultado() throws Exception {
    vacio(baja(ID, "vinculada"));
    UUID otro = InspeccionConsultaTest.CAMBIOS;
    jdbc.sql("update inspeccion set estado = 'BORRADOR' where id = :id").param("id", otro).update();
    List<String> antes = instantanea();
    problema(baja(otro, "vinculada"), 409, "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void claveDeAvanceNoSeConvierteEnBaja() throws Exception {
    MvcResult avance = avanzar("compartida");
    assertThat(avance.getResponse().getStatus()).isEqualTo(200);
    List<String> antes = instantanea();
    problema(baja(ID, "compartida"), 409, "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void legadoJsonbNoSeConvierteNiEjecuta() throws Exception {
    jdbc.sql(
            "insert into idempotencia (id, usuario_id, clave, estado_http, respuesta) "
                + "values (:id, :autor, 'legado-baja', 204, '{}'::jsonb)")
        .param("id", UUID.randomUUID())
        .param("autor", AUTOR)
        .update();
    List<String> antes = instantanea();
    problema(baja(ID, "legado-baja"), 409, "clave-idempotencia-incompatible");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void sinClaveOBlancaConserva204Luego404SinReserva(String clave) throws Exception {
    vacio(baja(ID, clave.isEmpty() ? null : clave));
    assertThat(auditorias()).isEqualTo(1);
    assertThat(resultados(clave.strip())).isZero();
    List<String> antes = instantanea();
    problema(baja(ID, clave.isEmpty() ? null : clave), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void nuevaClaveTrasBajaNoEsReplayNiCreaReserva() throws Exception {
    vacio(baja(ID, "original"));
    List<String> antes = instantanea();
    problema(baja(ID, "nueva"), 404, "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(resultados("nueva")).isZero();
  }

  @Test
  void limiteUnicode100Y101SinEfectosEnRechazo() throws Exception {
    List<String> antes = instantanea();
    problema(baja(ID, "😀".repeat(101)), 400, "idempotencia-invalida");
    assertThat(instantanea()).isEqualTo(antes);
    vacio(baja(ID, "😀".repeat(100)));
    antes = instantanea();
    vacio(baja(ID, "😀".repeat(100)));
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(resultados("😀".repeat(100))).isEqualTo(1);
  }

  @Test
  void erroresDeExistenciaAutorYEstadoNoCacheanNiMutan() throws Exception {
    List<String> antes = instantanea();
    problema(baja(UUID.randomUUID(), "ausente"), 404, "inspeccion-no-encontrada");
    problema(baja(InspeccionConsultaTest.BORRADOR_E2, "ajena"), 404, "inspeccion-no-encontrada");
    problema(baja(InspeccionConsultaTest.ELIMINADA, "eliminada"), 404, "inspeccion-no-encontrada");
    problema(baja(InspeccionConsultaTest.ENVIADA, "estado"), 409, "inspeccion-no-editable");
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql("update inspeccion set estado = 'BORRADOR' where id = :id")
        .param("id", InspeccionConsultaTest.ENVIADA)
        .update();
    vacio(baja(InspeccionConsultaTest.ENVIADA, "estado"));
    assertThat(resultados("estado")).isEqualTo(1);
  }

  @Test
  void falloRealGuardandoResultadoRevierteBajaYAuditoriaYPermiteReintentar() throws Exception {
    List<String> antes = instantanea();
    jdbc.sql(
            "alter table idempotencia add constraint prueba_baja_resultado "
                + "check (clave <> 'fallo-baja')")
        .update();
    try {
      MvcResult fallo = baja(ID, "fallo-baja");
      assertThat(fallo.getResponse().getStatus()).isEqualTo(500);
      assertThat(fallo.getResponse().getContentType()).isEqualTo("application/problem+json");
      assertThat(problemaJson(fallo))
          .isEqualTo(
              java.util.Map.of(
                  "title",
                  "Internal Server Error",
                  "status",
                  500,
                  "detail",
                  "Ocurrió un error inesperado.",
                  "instance",
                  BASE + ID));
    } finally {
      jdbc.sql("alter table idempotencia drop constraint prueba_baja_resultado").update();
    }
    assertThat(instantanea()).isEqualTo(antes);
    vacio(baja(ID, "fallo-baja"));
    assertThat(resultados("fallo-baja")).isEqualTo(1);
    assertThat(auditorias()).isEqualTo(1);
  }

  @Test
  void mismaClaveConcurrenteConfirmaUnaBajaYUnResultado() throws Exception {
    CountDownLatch inicio = new CountDownLatch(1);
    long version = version();
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var una =
          ejecutor.submit(
              () -> {
                inicio.await();
                return baja(ID, "concurrente");
              });
      var otra =
          ejecutor.submit(
              () -> {
                inicio.await();
                return baja(ID, "concurrente");
              });
      inicio.countDown();
      vacio(una.get(10, TimeUnit.SECONDS));
      vacio(otra.get(10, TimeUnit.SECONDS));
    }
    assertThat(version()).isEqualTo(version + 1);
    assertThat(auditorias()).isEqualTo(1);
    assertThat(resultados("concurrente")).isEqualTo(1);
  }

  @Test
  void esperaDosSegundosNoCacheaYPermiteReintento() throws Exception {
    String clave = "espera-baja";
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
                var pendiente = ejecutor.submit(() -> baja(ID, clave));
                try {
                  problema(pendiente.get(5, TimeUnit.SECONDS), 409, "operacion-en-proceso");
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
    }
    assertThat(instantanea()).isEqualTo(antes);
    vacio(baja(ID, clave));
    assertThat(resultados(clave)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"avance", "envio"})
  void escrituraConcurrenteConfirmadaNoSePierdePorUnaBajaLeidaAntes(String competidora)
      throws Exception {
    if (competidora.equals("envio")) prepararEnvio();
    long notificacionesAntes = Long.parseLong(instantanea().get(6));
    AtomicReference<List<String>> estadoRival = new AtomicReference<>();
    AtomicReference<Future<MvcResult>> pendiente = new AtomicReference<>();
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      new TransactionTemplate(gestor)
          .executeWithoutResult(
              tx -> {
                jdbc.sql("select id from inspeccion where id = :id for update")
                    .param("id", ID)
                    .query(UUID.class)
                    .single();
                pendiente.set(ejecutor.submit(() -> baja(ID, "carrera-baja")));
                esperarEscritorBloqueado();
                // MockMvc ejecuta el rival sin clave en el hilo que posee esta transacción;
                // sus servicios participan y su commit precede al UPDATE bloqueado de DELETE.
                try {
                  MvcResult rival =
                      competidora.equals("envio")
                          ? mvc.perform(
                                  post(BASE + ID + "/envio")
                                      .contentType(MediaType.APPLICATION_JSON)
                                      .content("{}")
                                      .with(como(Rol.ESTUDIANTE, AUTOR)))
                              .andReturn()
                          : avanzar(null);
                  assertThat(rival.getResponse().getStatus()).isEqualTo(200);
                  // NotificacionService.save participa en esta transacción, pero no hace flush.
                  // JdbcClient no sincroniza el contexto JPA: materializar TODO el envío antes
                  // de capturar el esperado evita atribuir su INSERT de commit a la baja perdedora.
                  entidades.flush();
                  estadoRival.set(instantanea());
                  assertThat(Long.parseLong(estadoRival.get().get(6)))
                      .isEqualTo(notificacionesAntes + (competidora.equals("envio") ? 1 : 0));
                  if (competidora.equals("envio")) {
                    assertThat(
                            (String)
                                JsonPath.read(rival.getResponse().getContentAsString(), "$.estado"))
                        .isEqualTo("ENVIADA");
                  }
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
      MvcResult perdedora = pendiente.get().get(10, TimeUnit.SECONDS);
      assertThat(perdedora.getResponse().getStatus()).isEqualTo(500);
      assertThat(perdedora.getResponse().getContentType()).isEqualTo("application/problem+json");
    }
    assertThat(instantanea()).isEqualTo(estadoRival.get());
    assertThat(
            jdbc.sql("select eliminado_en from inspeccion where id = :id")
                .param("id", ID)
                .query(java.time.Instant.class)
                .optional())
        .isEmpty();
    assertThat(resultados("carrera-baja")).isZero();
    assertThat(
            jdbc.sql(
                    "select count(*) from auditoria where entidad_id = :id "
                        + "and accion = 'DAR_DE_BAJA'")
                .param("id", ID)
                .query(Long.class)
                .single())
        .isZero();
    if (competidora.equals("envio")) {
      List<String> antes = instantanea();
      problema(baja(ID, "carrera-baja"), 409, "inspeccion-no-editable");
      assertThat(instantanea()).isEqualTo(antes);
    } else {
      vacio(baja(ID, "carrera-baja"));
      assertThat(resultados("carrera-baja")).isEqualTo(1);
    }
  }

  @Test
  void bajaConfirmadaImpideAvanceYEnvioPosterioresSinEfectos() throws Exception {
    vacio(baja(ID, "primera"));
    List<String> antes = instantanea();
    problema(avanzar(null), 404, "inspeccion-no-encontrada");
    problema(
        mvc.perform(
                post(BASE + ID + "/envio")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(como(Rol.ESTUDIANTE, AUTOR)))
            .andReturn(),
        404,
        "inspeccion-no-encontrada");
    assertThat(instantanea()).isEqualTo(antes);
    vacio(baja(ID, "primera"));
    assertThat(instantanea()).isEqualTo(antes);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clavesDistintasOSinClaveNoConfirmanDosBajasConcurrentes(boolean sinClave) throws Exception {
    long version = version();
    String relaciones = relaciones();
    AtomicReference<Future<MvcResult>> una = new AtomicReference<>();
    AtomicReference<Future<MvcResult>> otra = new AtomicReference<>();
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      new TransactionTemplate(gestor)
          .executeWithoutResult(
              tx -> {
                jdbc.sql("select id from inspeccion where id = :id for update")
                    .param("id", ID)
                    .query(UUID.class)
                    .single();
                una.set(ejecutor.submit(() -> baja(ID, "competidora-uno")));
                otra.set(ejecutor.submit(() -> baja(ID, sinClave ? null : "competidora-dos")));
                esperarEscritoresBloqueados(2);
              });
      MvcResult primera = una.get().get(10, TimeUnit.SECONDS);
      MvcResult segunda = otra.get().get(10, TimeUnit.SECONDS);
      assertThat(List.of(primera.getResponse().getStatus(), segunda.getResponse().getStatus()))
          .containsExactlyInAnyOrder(204, 500);
      MvcResult ganadora = primera.getResponse().getStatus() == 204 ? primera : segunda;
      MvcResult perdedora = primera.getResponse().getStatus() == 500 ? primera : segunda;
      vacio(ganadora);
      assertThat(perdedora.getResponse().getContentType()).isEqualTo("application/problem+json");
      assertThat(problemaJson(perdedora))
          .isEqualTo(
              java.util.Map.of(
                  "title",
                  "Internal Server Error",
                  "status",
                  500,
                  "detail",
                  "Ocurrió un error inesperado.",
                  "instance",
                  BASE + ID));
      String claveGanadora =
          primera.getResponse().getStatus() == 204
              ? "competidora-uno"
              : sinClave ? null : "competidora-dos";
      String clavePerdedora =
          primera.getResponse().getStatus() == 500
              ? "competidora-uno"
              : sinClave ? null : "competidora-dos";
      assertThat(version()).isEqualTo(version + 1);
      assertThat(auditorias()).isEqualTo(1);
      assertThat(relaciones()).isEqualTo(relaciones);
      assertThat(resultados("competidora-uno") + resultados("competidora-dos"))
          .isEqualTo(claveGanadora == null ? 0 : 1);
      List<String> antes = instantanea();
      problema(baja(ID, clavePerdedora), 404, "inspeccion-no-encontrada");
      if (claveGanadora != null) vacio(baja(ID, claveGanadora));
      assertThat(instantanea()).isEqualTo(antes);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void perdidaDeAutorDuranteEsperaDeniegaOperacionOReplaySinEfectos(boolean replay)
      throws Exception {
    String clave = "pierde-autor";
    if (replay) vacio(baja(ID, clave));
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
    AtomicReference<Future<MvcResult>> pendiente = new AtomicReference<>();
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      new TransactionTemplate(gestor)
          .executeWithoutResult(
              tx -> {
                jdbc.sql("select pg_advisory_xact_lock(:id)")
                    .param("id", bloqueo)
                    .query(Object.class)
                    .single();
                pendiente.set(ejecutor.submit(() -> baja(ID, clave)));
                esperarIntentoAdvisory();
                jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
                    .param("autor", InspeccionConsultaTest.E2)
                    .param("id", ID)
                    .update();
              });
      List<String> antes = instantanea();
      problema(pendiente.get().get(10, TimeUnit.SECONDS), 404, "inspeccion-no-encontrada");
      assertThat(instantanea()).isEqualTo(antes);
    }
    assertThat(resultados(clave)).isEqualTo(replay ? 1 : 0);
    assertThat(auditorias()).isEqualTo(replay ? 1 : 0);
  }

  private void esperarIntentoAdvisory() {
    long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < limite) {
      // pg_stat_activity puede retener snapshot dentro de la transacción observadora.
      jdbc.sql("select pg_stat_clear_snapshot()").query(Object.class).single();
      long cantidad =
          jdbc.sql(
                  """
          select count(*) from pg_stat_activity
          where pid <> pg_backend_pid() and datname = current_database()
          and xact_start is not null and query like '%pg_try_advisory_xact_lock%'
          """)
              .query(Long.class)
              .single();
      if (cantidad > 0) return;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("DELETE no intentó adquirir la clave retenida.");
  }

  private void prepararEnvio() {
    // Misma forma de fixture publicable que InspeccionEnvioTest.preparar/insertarFoto
    // (helpers privados): no hay escritura de transición o auditoría en la preparación.
    jdbc.sql(
            "update inspeccion set datos = cast(:datos as jsonb), "
                + "datos_permanentes_confirmados_en = TIMESTAMPTZ '2026-10-20T12:00:00Z', "
                + "fecha_inspeccion = DATE '2026-10-20' where id = :id")
        .param("datos", InspeccionEnvioTest.DATOS_COMPLETOS)
        .param("id", ID)
        .update();
    for (int i = 0; i < 4; i++) {
      UUID archivo = UUID.randomUUID();
      jdbc.sql(
              "insert into archivo (id, inspeccion_id, tipo, clave, clave_miniatura, tamano) "
                  + "values (:id, :ins, 'WEBP', :clave, :mini, 10)")
          .param("id", archivo)
          .param("ins", ID)
          .param("clave", "envio/" + ID + "/" + archivo + ".webp")
          .param("mini", "envio/" + ID + "/" + archivo + "-mini.webp")
          .update();
    }
  }

  private MvcResult baja(UUID id, String clave) throws Exception {
    return baja(id, clave, AUTOR, Rol.ESTUDIANTE);
  }

  private MvcResult baja(UUID id, String clave, UUID actor, Rol rol) throws Exception {
    var peticion = delete(BASE + id).with(como(rol, actor));
    if (clave != null) peticion.header("Idempotency-Key", clave);
    return mvc.perform(peticion).andReturn();
  }

  private MvcResult avanzar(String clave) throws Exception {
    var peticion =
        put(BASE + ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"datos\":{\"identificacion\":{\"tipologia\":\"arco\"}},\"version\":"
                    + version()
                    + "}")
            .with(como(Rol.ESTUDIANTE, AUTOR));
    if (clave != null) peticion.header("Idempotency-Key", clave);
    return mvc.perform(peticion).andReturn();
  }

  private static void vacio(MvcResult respuesta) {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(204);
    assertThat(respuesta.getResponse().getContentAsByteArray()).isEmpty();
    assertThat(respuesta.getResponse().getContentType()).isNull();
  }

  private static java.util.Map<String, Object> problemaJson(MvcResult respuesta) throws Exception {
    return JsonPath.read(respuesta.getResponse().getContentAsString(), "$");
  }

  private static void problema(MvcResult respuesta, int estado, String tipo) throws Exception {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(estado);
    assertThat(respuesta.getResponse().getContentType()).isEqualTo("application/problem+json");
    String[] textos =
        switch (tipo) {
          case "inspeccion-no-encontrada" ->
              new String[] {
                "Inspección no encontrada",
                "La inspección no existe o no está visible para su cuenta."
              };
          case "inspeccion-no-editable" ->
              new String[] {
                "La inspección no admite cambios",
                "Solo un borrador puede modificar datos, fecha, acompañantes o eliminarse."
              };
          case "clave-idempotencia-incompatible" ->
              new String[] {
                "Clave de idempotencia incompatible",
                "La clave ya está asociada a otra huella o a un resultado heredado no verificable."
              };
          case "idempotencia-invalida" ->
              new String[] {
                "Clave de idempotencia inválida", "Idempotency-Key no puede superar 100 caracteres."
              };
          case "operacion-en-proceso" ->
              new String[] {
                "Operación en proceso",
                "La operación sigue en curso; se puede reintentar con la misma clave."
              };
          default -> throw new AssertionError("Problema no previsto: " + tipo);
        };
    assertThat(problemaJson(respuesta))
        .isEqualTo(
            java.util.Map.of(
                "type",
                "https://sgp.cunoc.usac.edu.gt/errores/" + tipo,
                "title",
                textos[0],
                "status",
                estado,
                "detail",
                textos[1],
                "instance",
                respuesta.getRequest().getRequestURI()));
  }

  private long version() {
    return jdbc.sql("select version from inspeccion where id = :id")
        .param("id", ID)
        .query(Long.class)
        .single();
  }

  private long resultados(String clave) {
    return jdbc.sql("select count(*) from idempotencia where usuario_id = :id and clave = :clave")
        .param("id", AUTOR)
        .param("clave", clave)
        .query(Long.class)
        .single();
  }

  private long auditorias() {
    return jdbc.sql(
            "select count(*) from auditoria where entidad = 'inspeccion' and entidad_id = :id")
        .param("id", ID)
        .query(Long.class)
        .single();
  }

  private String relaciones() {
    return jdbc.sql(
            "select coalesce(jsonb_agg(to_jsonb(a) order by id)::text, '[]') "
                + "from acompanante_inspeccion a where inspeccion_id = :id")
        .param("id", ID)
        .query(String.class)
        .single();
  }

  private List<String> instantanea() {
    return jdbc.sql(
            """
        select
          (select coalesce(jsonb_agg(to_jsonb(i) order by id)::text, '[]') from inspeccion i),
          (select coalesce(jsonb_agg(to_jsonb(a) order by id)::text, '[]') from acompanante_inspeccion a),
          (select count(*)::text from auditoria),
          (select count(*)::text from idempotencia),
          (select count(*)::text from transicion_inspeccion),
          (select count(*)::text from copia_conflicto),
          (select count(*)::text from notificacion),
          (select coalesce(jsonb_agg(to_jsonb(a) order by id)::text, '[]') from archivo a)
        """)
        .query(
            (rs, n) ->
                List.of(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getString(7),
                    rs.getString(8)))
        .single();
  }

  private void esperarEscritorBloqueado() {
    esperarEscritoresBloqueados(1);
  }

  private void esperarEscritoresBloqueados(int esperados) {
    long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < limite) {
      long cantidad =
          jdbc.sql(
                  """
          with recursive cadena(pid) as (
            select pg_backend_pid()
            union
            select l.pid from pg_locks l join cadena c
              on c.pid = any(pg_blocking_pids(l.pid)) where not l.granted
          ) select count(*) - 1 from cadena
          """)
              .query(Long.class)
              .single();
      if (cantidad >= esperados) return;
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("DELETE no llegó al bloqueo PostgreSQL.");
  }
}
