package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class InspeccionPermanentesIdempotenciaTest extends ContextoCapturaIdempotenciaHttp {
  @Override
  String sufijo() {
    return "/confirmacion-datos-permanentes";
  }

  @Override
  String cuerpo(boolean nuevo) {
    return "{\"datos\":{\"identificacion\":{\"tipologia\":\""
        + (nuevo ? "arco" : "losas")
        + "\",\"numero_tramos\":3}}}";
  }

  @Override
  String invalido() {
    return "{\"datos\":{\"subestructura\":{\"estribo_entrada\":{\"calificacion\":2}}}}";
  }

  @Test
  void canonizaObjetosYNumerosSinReconfirmar() throws Exception {
    MvcResult primera = operar(ID, "canonica", cuerpo(false));
    exito(primera);
    List<String> antes = instantanea();
    igual(
        operar(
            ID,
            "canonica",
            "{\"datos\":{\"identificacion\":{\"numero_tramos\":3e0,\"tipologia\":\"losas\"}}}"),
        primera);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void replayNoRecalculaOrigenTrasOtraPublicacionYConfirmacion() throws Exception {
    MvcResult primera = operar(ID, "origen-viejo", cuerpo(false));
    exito(primera);
    jdbc.sql("update inspeccion set fecha_inspeccion = DATE '2099-01-01' where id = :id")
        .param("id", InspeccionConsultaTest.PUBLICADA_PRO)
        .update();
    exito(operar(ID, "origen-nuevo", cuerpo(true)));
    assertThat(
            jdbc.sql("select precargada_de_id from inspeccion where id = :id")
                .param("id", ID)
                .query(UUID.class)
                .single())
        .isEqualTo(InspeccionConsultaTest.PUBLICADA_PRO);
    List<String> antes = instantanea();
    igual(operar(ID, "origen-viejo", cuerpo(false)), primera);
    assertThat(instantanea()).isEqualTo(antes);
  }
}

/** Mismo contrato observable para M03 y M04; ninguna prueba invoca servicios internos. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/inspeccion/consulta.sql", "/inspeccion/borrador.sql"})
@Sql(
    scripts = "/inspeccion/consulta-limpiar.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
abstract class ContextoCapturaIdempotenciaHttp extends PruebaIntegracion {
  static final String BASE = "/api/v1/inspecciones/";
  static final UUID ID = InspeccionConsultaTest.BORRADOR_E1;
  static final UUID OTRO = InspeccionPrecargaTest.CORRECCION;
  static final UUID AUTOR = InspeccionConsultaTest.E1;
  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager gestor;

  abstract String sufijo();

  abstract String cuerpo(boolean nuevo);

  abstract String invalido();

  @Test
  void replayExactoNoRepiteActividadVersionRelacionesNiAuditoria() throws Exception {
    MvcResult primera = operar(ID, " exacta ", cuerpo(false));
    exito(primera);
    List<String> antes = instantanea();
    igual(operar(ID, "exacta", cuerpo(false)), primera);
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(resultados()).isEqualTo(1);
    assertThat(auditorias()).isEqualTo(1);
  }

  @Test
  void replayTardioNoRestauraEdicionPosteriorNiExigeEditabilidad() throws Exception {
    MvcResult primera = operar(ID, "anterior", cuerpo(false));
    exito(primera);
    exito(operar(ID, "posterior", cuerpo(true)));
    List<String> antes = instantanea();
    igual(operar(ID, "anterior", cuerpo(false)), primera);
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql(
            "update inspeccion set estado = 'PUBLICADA', publicada_en = now(), indice_condicion = 80, estado_calculado = 'BUENO', estado_confirmado = 'BUENO' where id = :id")
        .param("id", ID)
        .update();
    antes = instantanea();
    igual(operar(ID, "anterior", cuerpo(false)), primera);
    assertThat(instantanea()).isEqualTo(antes);
    problema(operar(ID, "nueva-cerrada", cuerpo(false)), 409, "inspeccion-no-editable");
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void cuerpoRecursoYOperacionDistintosNoReutilizanResultadoNiMutan() throws Exception {
    exito(operar(ID, "vinculada", cuerpo(false)));
    List<String> antes = instantanea();
    incompatible(operar(ID, "vinculada", cuerpo(true)));
    incompatible(operar(OTRO, "vinculada", cuerpo(false)));
    incompatible(
        enviar(put(BASE + ID), "vinculada", "{\"datos\":{},\"version\":0}", Rol.ESTUDIANTE));
    incompatible(enviar(post(BASE + ID + "/envio"), "vinculada", "{}", Rol.ESTUDIANTE));
    MockHttpServletRequestBuilder hermana =
        sufijo().equals("/acompanantes")
            ? post(BASE + ID + "/confirmacion-datos-permanentes")
            : put(BASE + ID + "/acompanantes");
    incompatible(
        enviar(
            hermana,
            "vinculada",
            sufijo().equals("/acompanantes") ? "{\"datos\":{}}" : "{\"acompanantes\":[]}",
            Rol.ESTUDIANTE));
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void autorActualYRolSeCompruebanAntesDeRevelarReplay() throws Exception {
    exito(operar(ID, "permiso", cuerpo(false)));
    List<String> antes = instantanea();
    assertThat(
            enviar(ruta(ID), "permiso", cuerpo(false), Rol.ADMINISTRADOR).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(instantanea()).isEqualTo(antes);
    jdbc.sql("update inspeccion set autor_id = :autor where id = :id")
        .param("autor", InspeccionConsultaTest.E2)
        .param("id", ID)
        .update();
    antes = instantanea();
    MvcResult referencia = operar(ID, null, cuerpo(false));
    assertThat(referencia.getResponse().getStatus()).isEqualTo(404);
    igual(operar(ID, "permiso", cuerpo(false)), referencia);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void eliminadaNoPermiteReplayYNoCreaReserva() throws Exception {
    exito(operar(ID, "eliminada", cuerpo(false)));
    jdbc.sql("update inspeccion set eliminado_en = now() where id = :id").param("id", ID).update();
    List<String> antes = instantanea();
    MvcResult referencia = operar(ID, null, cuerpo(false));
    assertThat(referencia.getResponse().getStatus()).isEqualTo(404);
    igual(operar(ID, "eliminada", cuerpo(false)), referencia);
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void legadoJsonbSeRechazaSinConvertirNiEjecutar() throws Exception {
    jdbc.sql(
            "insert into idempotencia (id, usuario_id, clave, estado_http, respuesta) values (:id, :usuario, 'legado', 200, '{}'::jsonb)")
        .param("id", UUID.randomUUID())
        .param("usuario", AUTOR)
        .update();
    List<String> antes = instantanea();
    incompatible(operar(ID, "legado", cuerpo(false)));
    assertThat(instantanea()).isEqualTo(antes);
  }

  @Test
  void rechazoDeDominioNoCacheaNiCambiaListaOrigenTiempoActividadAuditoria() throws Exception {
    List<String> antes = instantanea();
    MvcResult sinClave = operar(ID, null, invalido());
    assertThat(sinClave.getResponse().getStatus()).isEqualTo(422);
    igual(operar(ID, "rechazo", invalido()), sinClave);
    assertThat(instantanea()).isEqualTo(antes);
    exito(operar(ID, "rechazo", cuerpo(false)));
    assertThat(resultados()).isEqualTo(1);
  }

  @Test
  void sinClaveYBlancaConservanEfectosYBytesSalvoCamposTemporales() throws Exception {
    MvcResult sinClave = operar(ID, null, cuerpo(false));
    exito(sinClave);
    String datos = detalleDatos();
    long auditorias = auditorias();
    MvcResult blanca = operar(ID, "   ", cuerpo(false));
    exito(blanca);
    assertThat(blanca.getResponse().getContentType())
        .isEqualTo(sinClave.getResponse().getContentType());
    assertThat(detalleDatos()).isEqualTo(datos);
    assertThat(auditorias()).isEqualTo(auditorias + 1);
    assertThat(resultados()).isZero();
  }

  @Test
  void claveUnicodeLimiteNoConfundeCaracteresConBytes() throws Exception {
    List<String> antes = instantanea();
    problema(operar(ID, "😀".repeat(101), cuerpo(false)), 400, "idempotencia-invalida");
    assertThat(instantanea()).isEqualTo(antes);
    MvcResult primera = operar(ID, "😀".repeat(100), cuerpo(false));
    exito(primera);
    antes = instantanea();
    igual(operar(ID, "😀".repeat(100), cuerpo(false)), primera);
    assertThat(instantanea()).isEqualTo(antes);
    assertThat(resultados()).isEqualTo(1);
  }

  @Test
  void checkRealDeResultadoRevierteTodosLosEfectosYPermiteReintento() throws Exception {
    List<String> antes = instantanea();
    jdbc.sql(
            "alter table idempotencia add constraint prueba_contexto_resultado check (clave <> 'fallo-contexto')")
        .update();
    try {
      MvcResult fallo = operar(ID, "fallo-contexto", cuerpo(false));
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
                  BASE + ID + sufijo()));
    } finally {
      jdbc.sql("alter table idempotencia drop constraint prueba_contexto_resultado").update();
    }
    assertThat(instantanea()).isEqualTo(antes);
    exito(operar(ID, "fallo-contexto", cuerpo(false)));
    assertThat(resultados()).isEqualTo(1);
    assertThat(auditorias()).isEqualTo(1);
  }

  @Test
  void mismaClaveConcurrenteGuardaUnaSolaOperacion() throws Exception {
    CountDownLatch inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var una =
          ejecutor.submit(
              () -> {
                inicio.await();
                return operar(ID, "concurrente", cuerpo(false));
              });
      var otra =
          ejecutor.submit(
              () -> {
                inicio.await();
                return operar(ID, "concurrente", cuerpo(false));
              });
      inicio.countDown();
      MvcResult primera = una.get(15, TimeUnit.SECONDS);
      exito(primera);
      igual(otra.get(15, TimeUnit.SECONDS), primera);
    }
    assertThat(resultados()).isEqualTo(1);
    assertThat(auditorias()).isEqualTo(1);
  }

  @Test
  void esperaDosSegundosNoCacheaConflictoYPermiteReintentar() throws Exception {
    String clave = "espera-contexto";
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
                var pendiente = ejecutor.submit(() -> operar(ID, clave, cuerpo(false)));
                try {
                  problema(pendiente.get(5, TimeUnit.SECONDS), 409, "operacion-en-proceso");
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
    }
    assertThat(instantanea()).isEqualTo(antes);
    exito(operar(ID, clave, cuerpo(false)));
    assertThat(resultados()).isEqualTo(1);
  }

  @Test
  void clavesDistintasConservanVersionYReviertePorCompletoLaPerdedora() throws Exception {
    // La ruta histórica de permanentes puede hacer dos flushes. Medir el contrato
    // sin clave evita imponer un incremento distinto al que esta integración preserva.
    long referencia = versionHttp(OTRO);
    exito(operar(OTRO, null, cuerpo(false)));
    long incremento = versionHttp(OTRO) - referencia;
    assertThat(incremento).isPositive();
    long version = versionHttp();
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var respuestas = new java.util.ArrayList<java.util.concurrent.Future<MvcResult>>();
      new TransactionTemplate(gestor)
          .executeWithoutResult(
              tx -> {
                bloquearRecurso();
                respuestas.add(ejecutor.submit(() -> operar(ID, "carrera-uno", cuerpo(false))));
                respuestas.add(ejecutor.submit(() -> operar(ID, "carrera-dos", cuerpo(true))));
                esperarEscritoresBloqueados(2);
              });
      MvcResult una = respuestas.get(0).get(10, TimeUnit.SECONDS);
      MvcResult otra = respuestas.get(1).get(10, TimeUnit.SECONDS);
      assertThat(List.of(una.getResponse().getStatus(), otra.getResponse().getStatus()))
          .containsExactlyInAnyOrder(200, 500);
      MvcResult ganadora = una.getResponse().getStatus() == 200 ? una : otra;
      String claveGanadora = una.getResponse().getStatus() == 200 ? "carrera-uno" : "carrera-dos";
      boolean ganoPrimera = una.getResponse().getStatus() == 200;
      detallePersistidoDe(ganadora);
      assertThat(versionHttp()).isEqualTo(version + incremento);
      assertThat(resultados()).isEqualTo(1);
      assertThat(auditorias()).isEqualTo(1);
      List<String> antes = instantanea();
      igual(operar(ID, claveGanadora, cuerpo(!ganoPrimera)), ganadora);
      assertThat(instantanea()).isEqualTo(antes);
      exito(operar(ID, ganoPrimera ? "carrera-dos" : "carrera-uno", cuerpo(ganoPrimera)));
      assertThat(versionHttp()).isEqualTo(version + 2 * incremento);
      assertThat(resultados()).isEqualTo(2);
      assertThat(auditorias()).isEqualTo(2);
      antes = instantanea();
      igual(operar(ID, claveGanadora, cuerpo(!ganoPrimera)), ganadora);
      assertThat(instantanea()).isEqualTo(antes);
    }
  }

  @Test
  void publicacionConcurrenteImpideConfirmarEscrituraLeidaComoBorrador() throws Exception {
    try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      var respuesta =
          new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<MvcResult>>();
      new TransactionTemplate(gestor)
          .executeWithoutResult(
              tx -> {
                bloquearRecurso();
                respuesta.set(
                    ejecutor.submit(() -> operar(ID, "carrera-publicacion", cuerpo(false))));
                esperarEscritoresBloqueados(1);
                jdbc.sql(
                        "update inspeccion set estado = 'PUBLICADA', publicada_en = now(), indice_condicion = 80, estado_calculado = 'BUENO', estado_confirmado = 'BUENO', version = version + 1 where id = :id")
                    .param("id", ID)
                    .update();
              });
      assertThat(respuesta.get().get(10, TimeUnit.SECONDS).getResponse().getStatus())
          .isEqualTo(500);
    }
    assertThat(resultados()).isZero();
    assertThat(auditorias()).isZero();
    assertThat(
            jdbc.sql("select datos_permanentes_confirmados_en from inspeccion where id = :id")
                .param("id", ID)
                .query(java.time.Instant.class)
                .optional())
        .isEmpty();
    assertThat(
            jdbc.sql("select nombre from acompanante_inspeccion where inspeccion_id = :id")
                .param("id", ID)
                .query(String.class)
                .list())
        .containsExactly("Par de campo");
    List<String> antes = instantanea();
    problema(operar(ID, "carrera-publicacion", cuerpo(false)), 409, "inspeccion-no-editable");
    assertThat(instantanea()).isEqualTo(antes);
  }

  private void bloquearRecurso() {
    jdbc.sql("select id from inspeccion where id = :id for update")
        .param("id", ID)
        .query(UUID.class)
        .single();
  }

  private void esperarEscritoresBloqueados(int cantidad) {
    long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < limite) {
      // pg_locks/pg_blocking_pids consultan los locks vivos, sin depender del texto SQL
      // ni del snapshot de actividad retenido por una transacción de monitorización.
      // Incluye al segundo reemplazo que espera al primero, que a su vez espera esta fila.
      long esperando =
          jdbc.sql(
                  """
          with recursive cadena(pid) as (
            select pg_backend_pid()
            union
            select l.pid from pg_locks l join cadena c
              on c.pid = any(pg_blocking_pids(l.pid))
            where not l.granted
          )
          select count(*) - 1 from cadena
          """)
              .query(Long.class)
              .single();
      if (esperando >= cantidad) return;
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    var bloqueos =
        jdbc.sql(
                """
        select pid::text || ':' || locktype || ':' || mode || ':' || pg_blocking_pids(pid)::text
        from pg_locks where not granted order by pid, locktype
        """)
            .query(String.class)
            .list();
    throw new AssertionError("No llegaron los escritores HTTP al bloqueo PostgreSQL: " + bloqueos);
  }

  private void detallePersistidoDe(MvcResult escritura) throws Exception {
    MvcResult detalle = mvc.perform(get(BASE + ID).with(como(Rol.ESTUDIANTE, AUTOR))).andReturn();
    exito(detalle);
    assertThat(detalle.getResponse().getContentType())
        .isEqualTo(escritura.getResponse().getContentType());
    java.util.Map<String, Object> esperado =
        com.jayway.jsonpath.JsonPath.read(escritura.getResponse().getContentAsString(), "$");
    Object confirmacion = esperado.get("datos_permanentes_confirmados_en");
    if (confirmacion != null) {
      // GET lee timestamptz de precisión microsegundo; el ACK conserva el Instant
      // original en memoria. Esta conversión NO se aplica a ningún replay.
      esperado.put(
          "datos_permanentes_confirmados_en",
          java.time.Instant.parse((String) confirmacion)
              .plusNanos(500)
              .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
              .toString());
    }
    java.util.Map<String, Object> actual =
        com.jayway.jsonpath.JsonPath.read(detalle.getResponse().getContentAsString(), "$");
    assertThat(actual).isEqualTo(esperado);
  }

  private long versionHttp() throws Exception {
    return versionHttp(ID);
  }

  private long versionHttp(UUID id) throws Exception {
    MvcResult detalle = mvc.perform(get(BASE + id).with(como(Rol.ESTUDIANTE, AUTOR))).andReturn();
    exito(detalle);
    Number version =
        com.jayway.jsonpath.JsonPath.read(detalle.getResponse().getContentAsString(), "$.version");
    return version.longValue();
  }

  MvcResult operar(UUID id, String clave, String cuerpo) throws Exception {
    return enviar(ruta(id), clave, cuerpo, Rol.ESTUDIANTE);
  }

  private MockHttpServletRequestBuilder ruta(UUID id) {
    return sufijo().equals("/acompanantes")
        ? put(BASE + id + sufijo())
        : post(BASE + id + sufijo());
  }

  private MvcResult enviar(
      MockHttpServletRequestBuilder peticion, String clave, String cuerpo, Rol rol)
      throws Exception {
    peticion.with(como(rol, AUTOR)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    if (clave != null) peticion.header("Idempotency-Key", clave);
    return mvc.perform(peticion).andReturn();
  }

  static void exito(MvcResult respuesta) {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
    assertThat(respuesta.getResponse().getContentType()).isEqualTo("application/json");
  }

  static void igual(MvcResult actual, MvcResult esperado) {
    assertThat(actual.getResponse().getStatus()).isEqualTo(esperado.getResponse().getStatus());
    assertThat(actual.getResponse().getContentType())
        .isEqualTo(esperado.getResponse().getContentType());
    assertThat(actual.getResponse().getContentAsByteArray())
        .containsExactly(esperado.getResponse().getContentAsByteArray());
  }

  private void incompatible(MvcResult respuesta) throws Exception {
    problema(respuesta, 409, "clave-idempotencia-incompatible");
    assertThat(respuesta.getResponse().getContentAsString())
        .contains(
            "La clave ya está asociada a otra huella o a un resultado heredado no verificable.");
  }

  private static void problema(MvcResult respuesta, int estado, String tipo) throws Exception {
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(estado);
    assertThat(respuesta.getResponse().getContentType()).isEqualTo("application/problem+json");
    var problema = problemaJson(respuesta);
    assertThat(problema)
        .containsEntry("type", "https://sgp.cunoc.usac.edu.gt/errores/" + tipo)
        .containsEntry("status", estado)
        .containsEntry("instance", respuesta.getRequest().getRequestURI());
    String titulo;
    String detalle;
    switch (tipo) {
      case "clave-idempotencia-incompatible" -> {
        titulo = "Clave de idempotencia incompatible";
        detalle =
            "La clave ya está asociada a otra huella o a un resultado heredado no verificable.";
      }
      case "operacion-en-proceso" -> {
        titulo = "Operación en proceso";
        detalle = "La operación sigue en curso; se puede reintentar con la misma clave.";
      }
      case "idempotencia-invalida" -> {
        titulo = "Clave de idempotencia inválida";
        detalle = "Idempotency-Key no puede superar 100 caracteres.";
      }
      default -> {
        return;
      }
    }
    assertThat(problema)
        .isEqualTo(
            java.util.Map.of(
                "type", "https://sgp.cunoc.usac.edu.gt/errores/" + tipo,
                "title", titulo,
                "status", estado,
                "detail", detalle,
                "instance", respuesta.getRequest().getRequestURI()));
  }

  private static java.util.Map<String, Object> problemaJson(MvcResult respuesta) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(respuesta.getResponse().getContentAsString(), "$");
  }

  long resultados() {
    return jdbc.sql("select count(*) from idempotencia where usuario_id = :id")
        .param("id", AUTOR)
        .query(Long.class)
        .single();
  }

  long auditorias() {
    return jdbc.sql(
            "select count(*) from auditoria where entidad = 'inspeccion' and entidad_id = :id")
        .param("id", ID)
        .query(Long.class)
        .single();
  }

  private String detalleDatos() throws Exception {
    MvcResult detalle = mvc.perform(get(BASE + ID).with(como(Rol.ESTUDIANTE, AUTOR))).andReturn();
    exito(detalle);
    return com.jayway.jsonpath.JsonPath.read(detalle.getResponse().getContentAsString(), "$.datos")
        .toString();
  }

  List<String> instantanea() {
    return jdbc.sql(
            """
        select
          (select coalesce(jsonb_agg(to_jsonb(i) order by id)::text, '[]') from inspeccion i),
          (select coalesce(jsonb_agg(to_jsonb(a) order by id)::text, '[]') from acompanante_inspeccion a),
          (select count(*)::text from auditoria),
          (select count(*)::text from idempotencia),
          (select count(*)::text from transicion_inspeccion),
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
                    rs.getString(6),
                    rs.getString(7)))
        .single();
  }
}
